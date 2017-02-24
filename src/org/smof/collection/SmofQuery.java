package org.smof.collection;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.smof.element.Element;
import org.smof.parsers.SmofParser;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;

@SuppressWarnings("javadoc")
public class SmofQuery<T extends Element> implements FilterQuery<SmofQuery<T>>{

	private final Class<T> elementClass;
	private final FindIterable<BsonDocument> rawQuery;
	private final SmofParser parser;
	
	SmofQuery(Class<T> elementClass, FindIterable<BsonDocument> rawQuery, SmofParser parser) {
		this.elementClass = elementClass;
		this.rawQuery = rawQuery;
		this.parser = parser;
	}
	
	public Class<T> getElementClass() {
		return elementClass;
	}

	public SmofResults<T> results() {
		return new SmofResults<T>(rawQuery, parser, elementClass);
	}

	@Override
	public SmofQuery<T> withField(String fieldName, Object filterValue) {
		//validateFieldValue(fieldName, filterValue);
		rawQuery.filter(Filters.eq(fieldName, filterValue));
		return this;
	}
	
	public AndQuery<T> beginAnd() {
		return new AndQuery<>(this);
	}
	
	public OrQuery<T> beginOr() {
		return new OrQuery<>(this);
	}
	
	@Override
	public SmofQuery<T> applyBsonFilter(Bson filter) {
		rawQuery.filter(filter);
		return this;
	}
	
	public SmofQuery<T> applyBsonProjection(Bson projection) {
		rawQuery.projection(projection);
		return this;
	}
	
}
