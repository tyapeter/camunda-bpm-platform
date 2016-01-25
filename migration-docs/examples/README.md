### Example 1:

* A hat TaskListener/ExecutionListener

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A").to("V2_A")
  .create()
  .apply();
```

#### Vorher:

* Executions
* Task

#### Nachher:

* Executionbaum referenziert V2
  * Aktivitätsinstanz-ID bleibt gleich
* Task referenziert V2 und V2_A; Task-Id bleibt gleich (Begründung/Annahme: zwei 1:1-gemappte Tasks sind semantisch das gleich; sonst kann man Modification-API benutzen)
* sonstiger Task-State (z.B. Assignee) bleibt gleich
* Migration löst Tasklistener nicht aus; Ab Migration werden nur noch die Listener aus V2 aufgerufen

---

### Example 1 - Variablen migrieren:

* Prozessinstanzvariable X
* Taskvariable Y auf A

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A").to("V2_A")
  .create()
  .apply();
```

#### Vorher - Nachher

* wie oben
* Variablen werden beibehalten; da keine Executions/Tasks gelöscht werden, funktioniert das hier

---

### Example 1 - Variablen migrieren mit Transformation(Umbennung, Wertänderung, neue Variable hinzufügen):

* Prozessinstanzvariable W => Y
* Taskvariable X auf A => Z
* neue Variable U hinzufügen

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A").to("V2_A")
  .mappingListener(new MapMyVariablesListener())
  .create()
  .apply();
```

```
interface MapVariablesListener {

  // VariableScope reicht hier vermutlich nicht aus, wenn man die IDs bewahren möchte
  // man muss Variablen im richtigen Scope setzen können
  // man muss hier wissen auf welche Aktivitäten sich die Scopes beziehen
  void onMapppedActivityInstance(VariableScope source, VariableScope target);

  void onMappedTask(VariableScope source, VariableScope target);
}
```

#### Vorher:

* Executionbaum
* Taskinstanz
* W referenziert Prozessinstanz
* X referenziert Taskinstanz

#### Nachher:

* Taskinstanz und Executionbaum wie oben
* Variablenwert nicht verändert: Variablen-IDs bleiben gleich
* Variablenwert verändert: Variablen-IDs bleiben gleich
* Variablenname verändert: neue Variablen-ID
* neue Variableninstanz bekommt neue ID
* neue Variableninstanz wird im richtigen Scope erzeugt

---

### Example 2:

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A").to("V2_B", "V2_C")
  .create()
  .apply();
```

#### Vorher:
* Executionstate
* 1 Task für A

#### Nachher:

* Executionbaumzustand: Prozessinstanz, 2 concurrent Kinder
  * Prozessinstanz-Aktivitäts-ID zurücksetzen
  * CC-Executions für B und C erzeugen im Zustand, dass sie in dne Aktivitäten stehen (z.B. müssen gültige Aktiviätsinstanz-IDs erzeugt werden)
* 2 User Tasks für B und C
  * die Tasks werden neu erzeugt, denn sie bedeuten nicht semantisch dasselbe wie die Taskinstanz von A
  * Attribute (z.B. assignee) werden neu ausgewertet

---

### Example 2 - Variablen migrieren:

* Prozessinstanzvariable X
* Taskvariable Y auf A

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A").to("V2_B", "V2_C")
  .create()
  .apply();
```

#### Vorher - Nachher

* wie oben
* Prozessinstanzvariable X wird behalten (??????)
* Task-Variable Y wird verworfen, da kein 1:1-Mapping zu einem anderen Task
Variablen werden beibehalten; da keine Executions/Tasks gelöscht werden, funktioniert das hier

---

### Example 3:

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities().from("V1_A", "V1_B").to("V2_C")
  .create()
  .apply();
```

#### Vorher:

* Executionstate
* 2 Tasks für B, C

#### Nachher:

* Executionbaum: Prozessinstanz steht auf C mit neuer Aktivitätsinstanz-ID
* 1 User Task für C:
  * wurde neu erzeugt
  * mit Auswertung der Task-Attribute
  * Tasks für A und B wurden gelöscht (ohne Listeneraufrufe)




### Example 4:

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .mapActivities()
    .from("V1_A").to("V2_B").condition(new MyMigrationInstructionCondition())
    .from("V1_A").to("V2_C").condition(new MyMigrationInstructionCondition())
  .create()
  .apply();
```

```
interface MigrationInstructionCondition {

  boolean applies(ProcessInstance, ActivityInstanceId, ProcessEngine);
}
```

Implementation note:
* man sollte Bedingungen auswerten bevor man irgendwelche Instruktionen ausführt

### Example 5:

#### API:

```
repositoryService
  .createMigrationPlan("V1", "V2")
  .condition(new MyMigrationPlanCondition())
  .mapActivities().from("V1_A").to("V2_C")
  .create()
  .apply();
```

```
interface MigrationPlanCondition() {

  // Use Cases: Aktivitätsinstanzzustand, Business Key, Variablen...
  // wenn man die ganze Engine API hat, hat man maximale Flexibilität
  boolean applies(ProzessInstance, ProcessEngine);
}
```

---

### Example 6:

#### API:

* wie bei Example 1

#### Vorher:

* Executionbaum
* Eventsubscription

#### Nachher:

* Executionbaum bleibt gleich
* wenn die Message in der Zielprozessdefinition denselben Namen hat: Eventsubscription bleibt nur gleich
* wenn die Message eine andere ist: Eventsubscription wird neu erzeugt

---

### Example 7:

#### API:

* Wie bei Example 1

#### Vorher:

* Executionbaum
* asyncBefore-Job

#### Nachher:

* Executionbaum bleibt gleich
* es wird die Ausführung vor den Zielaktivitäten angestoßen (für asyncAfter analog danach); je nachdem ob die Zielaktivitäten ebenfalls async sind werden neue Jobs erzeugt => die Job-Instanz kann nicht dieselbe bleiben

---

### Example 7 - Job hat 0 Retries und einen Incident:

#### API:

* wie bei Example 7

#### Vorher:

* Executionbaum
* Job
* Incident

#### Nachher:

* wenn die Zielaktivität(en) äquivalent zur Quelle sind: Job und Attribute bleiben gleich; Incident wird beibehalten und ID-Atttribute migriert
* sonst: neuer Job wird erzeugt und anhand der Zielprozessdefinition initialisiert; Incident wird verworfen

---

### Example 7 - Job ist suspended:

wie im Falle von Retries und Incident ist Suspensionstate ein Attribut und es gelten die gleichen Regeln

---

### Example 7 - Job ist Timer:

### Nachher:

* wenn die gemappten Timer-Events äquivalent sind, dann bleibt die Job-Instanz samt Attributen dieselbe; sonst wird eine neue erzeugt

---

### Example 8 - External Tasks:

#### API:

* wie bei Example 1

#### Vorher:

* Executionbaum
* External-Task-Instanz

#### Nachher:

* Es gibt ein äquivalentes Mapping (d.h. eine Aktivität mit derselben External-Task-Topic): External-Task-Instanz wird mitsamt ihrer Attribute erhalten
* Sonst: Neue Instanz

---

### Example 9 - Call Activity:

#### API:

* wie Example 1

#### Vorher:

* Executionbaum
* aufgerufener Executionbaum

#### Nachher:

* wenn das Mapping für die Call ACtivity äquivalent (was ist hier genau äquivalent?): aufgerufene Prozessinstanz wird nicht verändert
* sonst: aufgerufene Instanz wird gelöscht und neue gestartet
* zusätzlich: man kann mehrere Migrationspläne in einen "Reaktor" geben und wenn es zwischen den aufgerufenen Prozessdefinitionen ein Mapping gibt, dann wird auch das ausgeführt

---

### Example 10 - Event-based Gateway:

* da ein event-based gateway wie ein wait state mit Boundary Events funktioniert, ist das hier kein großes Problem
* technisch sollte dasselbe passieren was mit anderen Boundary Events passiert

---

### Suspension State:

* beide Prozessdefinitionen nicht suspendet
  * vorher: PI suspendet => nachher: PI suspendet
* Zielprozessdefinition suspendet:
  * vorher: PI nicht suspendet => nachher:

### API-Struktur:
Migration Plan:
  * Proc-Def-Id -> Proc-Def-Id
  * Condition Callback
  * Variable Mapping Callback
  * Migration Instructions:
    * Activity Ids -> Activity Ids
    * Condition Callback
    * Variable Mapping Callback

### Algorithmus:

0: Ermittle, welche Instruktionen anwendbar sind
1: Sind die Execution-Bäume gleich?
  Ja: gehe zu 2a
  Nein:
    Executions von Quellaktivitätsinstanzen zerstören und entfernen (ohne Listener etc.)
    Executions für Zielaktivitätsinstanzen erzeugen (ohne Listener) (Schritt für Schritt für jede Instruktion)
    Gehe zu 2b
2a: Migrierte Tasks umhängen
2b: Behavior#execute ausführen mit den neuen Executions

### Gleichheit zwischen Aktivitäten:

* User Tasks
  * es gibt einen von uns festgelegten Rahmen, in dem Gleichheit überhaupt möglich ist (z.B. ist ein Mapping zwischen zwei unterschiedlichen Tasktypen niemals ein Gleichheitsmapping)
  * es scheint keinen eindeutigen Indikator zu geben (Gleichheit der IDs, Gleichheit der Aktivitätsnamen, 1:1-Mapping), den man automatisch aus den Prozessmodellen ableiten kann
  * deshalb müssen Nutzer die Möglichkeit haben das zu bestimmen
  * man kann sich auf ein bestimmtes Defaultverhalten festlegen; z.B. nimmt man Gleichheit der Aktivitätsnamen als Defaultindikator für Gleichheit der Aktivitäten

### Ausbaustufen:

* Executionbaum:
  * 1. Stufe: alle Executions werden einfach neu erzeugt (+ IDs); die Prozessinstanz-ID wird übernommen
  * 2. Stufe: wir versuchen möglichst viele Executions zu behalten (falls sie für Scopes zuständig sind, die in beiden Modellen existieren)
* Tasktypen:
  * 1. Stufe: User Task
  * n. Stufe: alles
* Mapping zwischen Tasktypen:
  * 1. Stufe: nur gleiche Typen
  * 2. Stufe: unterschiedliche Typen
* Variablen:
  * 1. Stufe: es gibt einen Callback um Variablen zu transportieren
  * n. Stufe: es gibt nützliche Defaults
* Bedingungen:
  * 1. Stufe: es gibt einen Callback um eine Bedingung auszudrücken
  * 2. Stufe: es gibt vordefiniert Callbacks, die man bequem in Cockpit verwenden kann (z.B. BusinessKey = x)
* Mappingarten:
  * 1. Stufe: 1:1 (Gleichheit und Nicht-Gleichheit)
  * 2. Stufe: 1:n, n:1, m:n
* Persistenz:
  * 1. Stufe: Migrations Plans sind transient
  * 2. Stufe: Migrations Pläne können gespeichert werden (Datenbank, Json Export)
* Ausführung:
  * 1. Stufe: Migrations Plan kann direkt auf einer Prozess Instanz ausgeführt werden
  * 2. Stufe: Migrations Plan kann auf mehreren Prozess Instanzen non-blocking
              ausgeführt (Jobs die batches von Instanzen migrarien)


#### Erster minimaler Proof of concept:

* Executionbaum: 1. Stufe
* Tasktypen: 1. Stufe
* Mapping zwischen Tasktypen: 1. Stufe
* Mappingarten: 1. Stufe
* Persistenz: 1. Stufe
* Ausführung: 1. Stufe

Für diese Stufe könnten wir eine REST API bereitstellen, die Sebastian S. dann in Cockpit verwenden könnte um erste Überlegungen bzgl. User Interface anzustellen


### Offene Fragen

* History/Reporting:
  * Wann wurden welche Instanzen migriert?
  * Behalten Instanzen ihre History?
  * Wie werden Fehler während der Batch Migration reportet und behandelt?
