package org.smof.parsers;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.bson.BsonBinary;
import org.bson.BsonValue;
import org.smof.collection.SmofDispatcher;
import org.smof.field.PrimaryField;
import org.smof.field.SmofField;

class ByteParser extends AbstractBsonParser {

	private static final Class<?>[] VALID_TYPES = {byte[].class, Byte[].class};
	
	protected ByteParser(SmofDispatcher dispatcher, SmofParser bsonParser) {
		super(dispatcher, bsonParser, VALID_TYPES);
		// TODO Auto-generated constructor stub
	}

	@SuppressWarnings("unchecked")
	@Override
	public BsonValue toBson(Object value, SmofField fieldOpts) {
		final Class<?> type = value.getClass();
		if(isPrimitiveByteArray(type)) {
			return fromPrimitiveByteArray((byte[]) value);
		}
		else if (isGenericByteArray(type)){
			return fromGenericByteArray((Byte[]) value);
		}
		else if(isCollection(type)) {
			return fromCollection((Collection<Byte>) value);
		}
		return null;
	}

	private BsonBinary fromCollection(Collection<Byte> value) {
		final Byte[] bytes = value.toArray(new Byte[value.size()]);
		return new BsonBinary(ArrayUtils.toPrimitive(bytes));
	}

	private boolean isCollection(Class<?> type) {
		return Collection.class.isAssignableFrom(type);
	}

	private BsonBinary fromGenericByteArray(Byte[] value) {
		return new BsonBinary(ArrayUtils.toPrimitive(value));
	}

	private boolean isGenericByteArray(Class<?> type) {
		return type.equals(Byte[].class);
	}

	private BsonBinary fromPrimitiveByteArray(byte[] value) {
		return new BsonBinary(value);
	}

	private boolean isPrimitiveByteArray(Class<?> type) {
		return type.equals(byte[].class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T fromBson(BsonValue rawValue, Class<T> type, SmofField fieldOpts) {
		final BsonBinary value = rawValue.asBinary();
		if(isPrimitiveByteArray(type)) {
			return (T) toPrimitiveByteArray(value);
		}
		else if(isGenericByteArray(type)) {
			return (T) toGenericByteArray(value);
		}
		else if(isCollection(type)) {
			if(isPrimaryField(fieldOpts)) {
				return (T) toByteCollection(value, type);
			}
			else if(isParameterField(fieldOpts)) {
				return (T) toByteCollection(value, type);
			}
		}
		return null;
	}

	private Collection<Byte> toByteCollection(BsonBinary value, Class<?> collectionClass) {
		final List<Byte> data = Arrays.asList(ArrayUtils.toObject(value.getData()));
		final Collection<Byte> collection = createCollection(collectionClass);
		collection.addAll(data);
		return collection;
	}
	
	private Collection<Byte> createCollection(Class<?> collectionClass) {
		final Collection<Byte> collection;
		if(List.class.isAssignableFrom(collectionClass)) {
			collection = new ArrayList<>();
		}
		else if(Set.class.isAssignableFrom(collectionClass)) {
			collection = new LinkedHashSet<>();
		}
		else {
			collection = null;
		}
		return collection;
	}

	private Byte[] toGenericByteArray(BsonBinary value) {
		return ArrayUtils.toObject(value.getData());
	}

	private byte[] toPrimitiveByteArray(BsonBinary value) {
		return value.getData();
	}

	@Override
	public boolean isValidBson(BsonValue value) {
		return super.isValidBson(value) || value.isBinary();
	}

	@Override
	public boolean isValidType(SmofField fieldOpts) {
		return super.isValidType(fieldOpts) || 
				(!isPrimaryField(fieldOpts) || isByteCollection((PrimaryField) fieldOpts));
	}

	private boolean isByteCollection(PrimaryField fieldOpts) {
		return isCollection(fieldOpts.getFieldClass()) && isByteComponentType(fieldOpts.getRawField());
	}

	private boolean isByteComponentType(Field field) {
		return getCollectionType(field).equals(Byte.class);
	}
	
	private Class<?> getCollectionType(Field collType) {
		final ParameterizedType mapParamType = (ParameterizedType) collType.getGenericType();
		return (Class<?>) mapParamType.getActualTypeArguments()[0];
	}

}
