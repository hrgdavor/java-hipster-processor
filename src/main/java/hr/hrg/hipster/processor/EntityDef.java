package hr.hrg.hipster.processor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.*;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import hr.hrg.hipster.dao.*;
import hr.hrg.hipster.sql.*;

public class EntityDef {

	public final String packageName;
	public final String simpleName;
	public final DeclaredType declaredType;

    List<Property> props = new ArrayList<>();
	private Property primaryProp;
	public final String tableName;
	public final boolean isInterface;
//	public final ClassName typeEnum;
	public final ClassName typeImmutable;
	public final ClassName typeBuilder;
	public final ClassName typeUpdate;
	public final ClassName typeMeta;
	public final ClassName typeDelta;
	public final ClassName type;

	public final boolean genMeta;
	public final boolean genUpdate;
	
	public EntityDef(TypeElement clazz, Elements elements){
		isInterface = clazz.getKind().isInterface();
		this.declaredType = (DeclaredType) clazz.asType();

		String[] entityNamesPrefix = HipsterSqlUtil.entityNamesPrefixArray(clazz);
		this.packageName = entityNamesPrefix[0];
		this.simpleName = entityNamesPrefix[1];
		
		HipsterEntity hipsterEntity = clazz.getAnnotation(HipsterEntity.class);
		
		if(hipsterEntity != null && hipsterEntity.table() != null && !hipsterEntity.table().isEmpty()) {
			this.tableName = hipsterEntity.table();
		} else {
			this.tableName = simpleName;
		}

		genMeta = hipsterEntity.genMeta();
		genUpdate = hipsterEntity.genUpdate();
		this.type = ClassName.get(clazz);

//		this.typeEnum      = ClassName.get(packageName, simpleName+"Enum");
		this.typeImmutable = ClassName.get(packageName, simpleName+"Immutable");
		this.typeBuilder   = ClassName.get(packageName, simpleName+"Builder");
		this.typeUpdate    = ClassName.get(packageName, simpleName+"Update");
		this.typeMeta      = ClassName.get(packageName, simpleName+"Meta");
		this.typeDelta     = ClassName.get(packageName, simpleName+"Delta");
		
	}
		
	public Property addProp(String name, TypeName typeName, TypeMirror typeMirror, ExecutableElement method, ProcessingEnvironment processingEnv){
		Property property = new Property(name, typeName, method, this.tableName, processingEnv);
		props.add(property);
		return property;
	}
	
	public List<Property> getProps() {
		return props;
	}

	public void setPrimaryProp(Property prop) {
		this.primaryProp = prop;
	}
	
	public Property getPrimaryProp() {
		return primaryProp;
	}
}
