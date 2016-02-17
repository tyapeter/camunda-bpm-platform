class Index {
    String name
    boolean unique
    String table
    String[] columns
    String toString() { "${unique?'UNIQ ':''}Index{$name on $table for $columns}" }
    boolean equals(Index o) { name.equals(o.name) }
}

def scriptFolder = 'src/main/resources/org/camunda/bpm/engine/db'
def createScriptFolder = scriptFolder + '/create'
def dropScriptFolder = scriptFolder + '/drop'

def errors = 0;

new File(createScriptFolder).eachFileMatch ~/.*\.sql/, { File createScriptFile ->
    def createScript = createScriptFile.text
    def createdIndexes = collectCreatedIndexes(createScript)

    def dropScriptFile = new File(dropScriptFolder + '/' + createScriptFile.name.replaceAll('create', 'drop'))
    def dropScript = dropScriptFile.text
    def droppedIndexes = collectDroppedIndexes(dropScript)

    // checks

    def notDroppedIndexes = createdIndexes.minus(droppedIndexes)
    if (notDroppedIndexes) {
        println "$createScriptFile contains not dropped indexes:"
        println "  ${notDroppedIndexes.name.join('\n  ')}"
        errors += notDroppedIndexes.size
    }

    def notCreatedIndexes = droppedIndexes.minus(createdIndexes)
    if (notCreatedIndexes) {
        println "$dropScriptFile contains not created indexes:"
        println "  ${notCreatedIndexes.name.join('\n  ')}"
        errors += notCreatedIndexes.size
    }

}

System.exit(errors);

def collectCreatedIndexes(script) {
    def pattern = '''(?i)create(\\s+unique)?\\s+index\\s+(?<index>\\w+)\\s+on\\s+(?<table>[^\\(]+)\\((?<colums>[^\\)]+)\\)'''
    (script =~ pattern).collect { matched, unique, index, table, columns ->
        new Index (
            name: index,
            unique: unique != null,
            table: table,
            columns: columns.split(',')
        )
    }
}

def collectDroppedIndexes(script) {
    def pattern = '''(?i)drop\\s+index\\s+((?<tablePrefix>\\w+)\\.)?(?<index>\\w+)(\\s+on\\s+(?<tableSuffix>\\w+))?'''
    (script =~ pattern).collect { matched, ignore, tablePrefix, index, ignore2, tableSuffix ->
        new Index (
            name: index,
            unique: false,
            table: tablePrefix ?: tableSuffix,
            columns: null
        )
    }
}


