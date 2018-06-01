package hr.hrg.hipster.processor;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.persistence.Column;
import javax.tools.Diagnostic.*;

import com.squareup.javapoet.*;

import hr.hrg.hipster.sql.*;
import hr.hrg.javapoet.PoetUtil;

class Property {
	/** final, and can only be set in constructor */
	public boolean readOnly;
	public String name;
	public String fieldName;
	public String getterName;
	public String setterName;
	public String columnName;
	public String tableName = "";
	public String sql = "";
	public TypeName type;
	public TypeName customType;
	public String customTypeKey = "";
	public ExecutableElement method;
	public boolean jsonIgnore;
	
	public Property(String getter, TypeName type, ExecutableElement method, String tableName){
		this.getterName = getter;
		this.type = type;
		this.tableName = tableName;
		String name = null;

		List<? extends AnnotationMirror> list = method.getAnnotationMirrors();
		if(list.size() >0){				
			for(AnnotationMirror mirror: list){
				if("com.fasterxml.jackson.annotation.JsonIgnore".equals(mirror.getAnnotationType().toString())){
					this.jsonIgnore = true;
				}
			}			
		}
		
		this.method = method;
		
		if(getter.startsWith("get")) {
			name = getter.substring(3);
		}else
			name = getter.substring(2);
		setterName = "set"+name;

		this.name = name = Character.toLowerCase(name.charAt(0))+name.substring(1);
		if(PoetUtil.isJavaKeyword(name)) this.name = "_"+name;
		this.fieldName = this.name;
		
		this.columnName = this.name;
		Column columnAnnotation = method.getAnnotation(Column.class);
		if(columnAnnotation != null){
			if(!columnAnnotation.name().isEmpty()) this.columnName = columnAnnotation.name();
			if(!columnAnnotation.table().isEmpty()) tableName = columnAnnotation.table();
		}
		
		HipsterColumn hipsterColumn = method.getAnnotation(HipsterColumn.class);
		if(hipsterColumn != null){
			if(!hipsterColumn.name().isEmpty()) this.columnName = columnAnnotation.name();
			this.sql = hipsterColumn.sql();
			if(!hipsterColumn.table().isEmpty()) this.tableName = columnAnnotation.table();
			try {
				if(hipsterColumn.customType() != ICustomType.class) {
					this.customType = ClassName.get(hipsterColumn.customType());// will likely always throw error					
				}
			}catch (MirroredTypeException e) {
				if(!"hr.hrg.hipster.sql.ICustomType".equals(e.getTypeMirror().toString())) {					
					this.customType = ClassName.get(e.getTypeMirror());
				}
			}
			this.customTypeKey = hipsterColumn.customTypeKey();
		}			
		
	}

	public boolean isPrimitive(){
		return type.isPrimitive();
	}
	
}