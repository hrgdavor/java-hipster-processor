package hr.hrg.hipster.processor;

import javax.lang.model.element.ExecutableElement;
import javax.persistence.Column;

import com.squareup.javapoet.TypeName;

import hr.hrg.javapoet.PoetUtil;

class Property {
	/** final, and can only be set in constructor */
	public boolean readOnly;
	public String name;
	public String fieldName;
	public String getterName;
	public String setterName;
	public String columnName;
	public TypeName type;
	
	public Property(String getter, TypeName type, ExecutableElement method){
		this.getterName = getter;
		this.type = type;
		String name = null;
		if(getter.startsWith("get")) {
			name = getter.substring(3);
		}else
			name = getter.substring(2);
		setterName = "set"+name;

		this.name = name = Character.toLowerCase(name.charAt(0))+name.substring(1);
		if(PoetUtil.isJavaKeyword(name)) this.name = "_"+name;
		this.fieldName = this.name;
		
		this.columnName = this.name;
		Column column = method.getAnnotation(Column.class);
		if(column != null && column.name() != null) this.columnName = column.name();
	}

	public boolean isPrimitive(){
		return type.isPrimitive();
	}
	
}