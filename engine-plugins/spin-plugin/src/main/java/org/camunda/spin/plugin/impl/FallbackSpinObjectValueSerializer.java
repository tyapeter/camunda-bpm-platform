/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.spin.plugin.impl;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.variable.serializer.AbstractObjectValueSerializer;

/**
 * TODO: Idea: this serializer is adhoc instantiated when no other serializer matches
 *
 * @author Thorben Lindhauer
 */
public class FallbackSpinObjectValueSerializer extends AbstractObjectValueSerializer {

  protected String serializationFormat;

  public FallbackSpinObjectValueSerializer(String serializationFormat) {
    super(serializationFormat);
    this.serializationFormat = serializationFormat;
  }

  public String getName() {
    return "spin://" + serializationFormat;
  }

  protected String getTypeNameForDeserialized(Object deserializedObject) {
    throw new ProcessEngineException("Fallback serializer cannot handle deserialized objects");
  }

  protected byte[] serializeToByteArray(Object deserializedObject) throws Exception {
    throw new ProcessEngineException("Fallback serializer cannot handle deserialized objects");
  }

  protected Object deserializeFromByteArray(byte[] object, String objectTypeName) throws Exception {
    throw new ProcessEngineException("Fallback serializer cannot handle deserialized objects");
  }

  protected boolean isSerializationTextBased() {
    return true;
  }

  protected boolean canSerializeValue(Object value) {
    throw new ProcessEngineException("Fallback serializer cannot handle deserialized objects");
  }

//  public void writeValue(ObjectValue value, ValueFields valueFields) {
//    if (value.isDeserialized()) {
//      throw new ProcessEngineException("Fallback serializer cannot handle serialization");
//    }
//
//    //TODO: null checks
//    String serializedStringValue = value.getValueSerialized();
//    byte[] serializedByteValue = StringUtil.toByteArray(serializedStringValue);
//
//  }
//
//  public ObjectValue readValue(ValueFields valueFields, boolean deserializeValue) {
//    if (deserializeValue) {
//      throw new ProcessEngineException("Fallback serializer cannot handle deserialization");
//    }
//
//    String stringValue = new String(valueFields.getByteArrayValue(), Context.getProcessEngineConfiguration().getDefaultCharset());
//
//    return createSerializedValue(stringValue, valueFields);
//  }
//
//  // TODO: consolidate with AbstractObjectValueSerializer
//  protected ObjectValue createSerializedValue(String serializedStringValue, ValueFields valueFields) {
//    String objectTypeName = readObjectNameFromFields(valueFields);
//    valueFields.getName();
//    return new ObjectValueImpl(null, serializedStringValue, serializationFormat, objectTypeName, false);
//  }
//
//  //TODO: consolidate with AbstractObjectValueSerializer
//  protected String readObjectNameFromFields(ValueFields valueFields) {
//    return valueFields.getTextValue2();
//  }
//
//  public ObjectValue convertToTypedValue(UntypedValueImpl untypedValue) {
//    // TODO Auto-generated method stub
//    return null;
//  }
//
//  protected boolean canWriteValue(TypedValue value) {
//    if (value instanceof SerializableValue) {
//      SerializableValue serializableValue = (SerializableValue) value;
//      String serialization
//    }
//    else {
//      return false;
//    }
//  }

}
