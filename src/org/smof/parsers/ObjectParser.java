package org.smof.parsers;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.smof.annnotations.SmofObject;
import org.smof.collection.SmofDispatcher;
import org.smof.element.Element;
import org.smof.exception.MissingRequiredFieldException;
import org.smof.field.ParameterField;
import org.smof.field.PrimaryField;
import org.smof.field.SecondaryField;
import org.smof.field.SmofField;

class ObjectParser extends AbstractBsonParser {

	private static final String ENUM_NAME = "_enumValue";

	ObjectParser(SmofParser parser, SmofDispatcher dispatcher) {
		super(dispatcher, parser);
	}

	@Override
	public BsonValue toBson(Object value, SmofField fieldOpts, SerializationContext serContext) {
		if(serContext.contains(value, fieldOpts.getType())) {
			return serContext.get(value, fieldOpts.getType());
		}
		final Class<?> type = value.getClass();
		final BsonDocument serValue;

		if(isMaster(fieldOpts)) {
			return fromMasterField((Element) value, fieldOpts, serContext);
		}
		else if(isElement(type)) {
			return fromElement((Element) value, serContext);
		}
		else if(isMap(type) && isPrimaryField(fieldOpts)) {
			return fromMap((Map<?, ?>) value, (PrimaryField) fieldOpts, serContext);
		}
		else if(isEnum(type)) {
			return fromEnum((Enum<?>) value, serContext);
		}
		serValue = fromObject(value, serContext);
		serContext.put(value, SmofType.OBJECT, serValue);
		return serValue;
	}

	@Override
	protected BsonValue toBson(Object value, SmofField fieldOpts) {
		// unused
		return null;
	}

	private BsonDocument fromMasterField(Element value, SmofField fieldOpts, SerializationContext serContext) {
		serContext.put(value, fieldOpts.getType(), toBsonObjectId(value));
		return fromObject(value, serContext);
	}

	private BsonObjectId toBsonObjectId(Element value) {
		final ObjectId id = value.getId();
		return new BsonObjectId(id);
	}

	private BsonObjectId fromElement(Element value, SerializationContext serContext) {
		final BsonObjectId id = toBsonObjectId(value);
		serContext.put(value, SmofType.OBJECT, id);
		dispatcher.insertChild(value);
		return id;
	}

	private BsonDocument fromObject(Object value, SerializationContext serContext) {
		final BsonDocument document = new BsonDocument();
		final TypeParser<?> metadata = getTypeParser(value.getClass());

		for(PrimaryField field : metadata.getAllFields()) {
			final Object fieldValue = extractValue(value, field);
			final BsonValue parsedValue;

			checkRequired(field, fieldValue);
			parsedValue = bsonParser.toBson(fieldValue, field, serContext);
			document.put(field.getName(), parsedValue);
		}
		return document;
	}

	private Object extractValue(Object element, PrimaryField field) {
		try {
			final Field rawField = field.getRawField();
			final Object value;
			rawField.setAccessible(true);
			value = rawField.get(element);
			rawField.setAccessible(false);
			return value;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			handleError(e);
			return null;
		}
	}

	private void checkRequired(PrimaryField field, Object value) {
		if(field.isRequired() && value == null) {
			final String name = field.getName();
			handleError(new MissingRequiredFieldException(name));
		}
	}

	private BsonDocument fromEnum(Enum<?> value, SerializationContext serContext) {
		final BsonDocument document = fromObject(value, serContext);
		final BsonString name = new BsonString(value.name());
		document.append(ENUM_NAME, name);
		return document;
	}

	private BsonDocument fromMap(Map<?, ?> value, PrimaryField mapField, SerializationContext serContext) {
		final Pair<SecondaryField, SecondaryField> fields = getMapFields(mapField);
		final BsonDocument document = new BsonDocument();
		for(Object key : value.keySet()) {
			final Object mapValue = value.get(key);
			final BsonValue parsedValue = bsonParser.toBson(mapValue, fields.getValue(), serContext);
			document.append(key.toString(), parsedValue);
		}
		return document;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T fromBson(BsonValue value, Class<T> type, SmofField fieldOpts) {
		if(isMaster(fieldOpts) && isElement(type)) {
			return toObject(value.asDocument(), type);
		}
		else if(isElement(type)) {
			return (T) toElement(value, (Class<Element>) type);
		}
		else if(isPrimaryField(fieldOpts) && isMap(type)) {
			return (T) toMap(value.asDocument(), (PrimaryField) fieldOpts);
		}
		else {
			return toObject(value.asDocument(), type);
		}
	}

	private <T extends Element> T toElement(BsonValue value, Class<T> type) {
		final ObjectId id = value.asObjectId().getValue();
		//all types are lazy loaded
		return bsonParser.createLazyInstance(type, id);
	}

	private Map<?, ?> toMap(BsonDocument document, PrimaryField fieldOpts) {
		final Map<Object, Object> map = new LinkedHashMap<>();
		final Pair<SecondaryField, SecondaryField> mapClass = getMapFields(fieldOpts);


		for(String bsonKey : document.keySet()) {
			final BsonValue bsonValue = document.get(bsonKey);
			final Object key = toMapKey(bsonKey, mapClass.getKey());
			final Object value = toMapValue(bsonValue, mapClass.getValue());
			map.put(key, value);
		}

		return map;
	}

	private Object toMapValue(BsonValue value, SecondaryField field) {
		return bsonParser.fromBson(value, field);
	}

	private Object toMapKey(String key, SecondaryField field) {
		final BsonString bsonKey = new BsonString(key);
		return bsonParser.fromBson(bsonKey, field);
	}

	private <T> T toObject(BsonDocument document, Class<T> type) {
		final BsonBuilder<T> builder = new BsonBuilder<T>();
		final T obj = buildObject(document, builder, type);
		fillObject(document, builder, obj);
		return obj;
	}

	private <T> void fillObject(BsonDocument document, final BsonBuilder<T> builder, final T obj) {
		final TypeParser<?> typeParser = getTypeParser(obj.getClass());
		for(PrimaryField field : typeParser.getNonBuilderFields()) {
			handleField(document, builder, field);
		}
		builder.fillElement(obj);
		setElementMetadata(document, obj);
	}

	private <T> void handleField(BsonDocument document, final BsonBuilder<T> builder, PrimaryField field) {
		final BsonValue fieldValue = document.get(field.getName());
		final Object parsedObj;
		if(fieldValue.isObjectId()) {
			builder.append2LazyElements(field, fieldValue.asObjectId().getValue());
		}
		else {
			parsedObj = bsonParser.fromBson(fieldValue, field);
			builder.append2AdditionalFields(field.getRawField(), parsedObj);
		}
	}

	private void setElementMetadata(BsonDocument document, Object obj) {
		if(obj instanceof Element) {
			((Element) obj).setId(document.getObjectId(Element.ID).getValue());
		}
	}

	private <T> T buildObject(BsonDocument document, BsonBuilder<T> builder, Class<T> type) {
		final TypeBuilder<T> typeBuilder = getTypeBuilder(type);
		for(ParameterField field : typeBuilder.getParams()) {
			final BsonValue fieldValue = getFromDocument(document, field.getName());
			final Object parsedObj;
			parsedObj = bsonParser.fromBson(fieldValue, field);
			builder.append(field.getName(), parsedObj);
		}
		return builder.build(typeBuilder);
	}

	private BsonValue getFromDocument(BsonDocument document, String field) {
		final BsonValue value = document.get(field);
		return value == null ? new BsonNull() : value;
	}

	@Override
	public boolean isValidType(SmofField fieldOpts) {
		final Class<?> type = fieldOpts.getFieldClass();
		return isValidType(type) && 
				(!isPrimaryField(fieldOpts) || isValidMap(type, (PrimaryField) fieldOpts));
	}

	@Override
	public boolean isValidType(Class<?> type) {
		return !isPrimitive(type) && !isArray(type);
	}

	private boolean isValidMap(Class<?> type, PrimaryField fieldOpts) {
		final Pair<SecondaryField, SecondaryField> mapTypes;

		checkValidMapOpts(fieldOpts);
		if(isMap(type)) {
			mapTypes = getMapFields(fieldOpts);

			return bsonParser.isValidType(mapTypes.getKey())
					&& bsonParser.isValidType(mapTypes.getValue());
		}
		return true;
	}

	private SmofType getMapValueType(PrimaryField fieldOpts) {
		final SmofObject note = fieldOpts.getSmofAnnotationAs(SmofObject.class);
		return note.mapValueType();
	}

	private Pair<SecondaryField, SecondaryField> getMapFields(PrimaryField mapMetadata) {
		final String name = mapMetadata.getName();
		final Field mapField = mapMetadata.getRawField();
		final SmofType valueType = getMapValueType(mapMetadata);
		final ParameterizedType mapParamType = (ParameterizedType) mapField.getGenericType();
		final Class<?> keyClass = (Class<?>) mapParamType.getActualTypeArguments()[0];
		final Class<?> valueClass = (Class<?>) mapParamType.getActualTypeArguments()[1];
		final SecondaryField keyMetadata = new SecondaryField(name, SmofType.STRING, keyClass);
		final SecondaryField valueMetadata = new SecondaryField(name, valueType, valueClass);
		return Pair.of(keyMetadata, valueMetadata);
	}

	private void checkValidMapOpts(PrimaryField fieldOpts) {
		if(fieldOpts == null) {
			handleError(new UnsupportedOperationException("Nested maps are not supported"));
		}
	}

	@Override
	public boolean isValidBson(BsonValue value) {
		return super.isValidBson(value) || value.isDocument() || value.isObjectId();
	}

}
