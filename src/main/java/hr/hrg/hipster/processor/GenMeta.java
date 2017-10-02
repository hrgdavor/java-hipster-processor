package hr.hrg.hipster.processor;

import static hr.hrg.hipster.processor.HipsterProcessorUtil.*;
import static hr.hrg.javapoet.PoetUtil.*;

import java.sql.*;

import com.squareup.javapoet.*;

import hr.hrg.hipster.dao.*;
import hr.hrg.hipster.dao.change.*;
import hr.hrg.hipster.sql.*;


public class GenMeta {

	public TypeSpec.Builder gen(EntityDef def) {
		
		TypeSpec.Builder cp = classBuilder(PUBLIC(), def.typeMeta);

		Property primaryProp = def.getPrimaryProp();
		TypeName primaryType = primaryProp == null ? TypeName.get(Object.class) : primaryProp.type;
		
		ClassName enumName = def.typeEnum;
		
		cp.addSuperinterface(parametrized(IEntityMeta.class,def.type, primaryType, enumName));
		
		FieldSpec resultGetterField = addField(cp,PUBLIC().FINAL(), ResultGetterSource.class, "getterSource");
		
		for(Property p:def.getProps()) {
			if(getterName(p) == null){
				addField(cp,PUBLIC().FINAL(), parametrized(IResultGetter.class, p.type), "_"+p.fieldName+"_resultGetter");
			}
		}

		MethodSpec.Builder constr = constructorBuilder(PUBLIC());
		addSetterParameter(constr, resultGetterField, null);
		for(Property p:def.getProps()) {
			if(getterName(p) == null){
				constr.addCode("_"+p.fieldName+"_resultGetter = ($T) getterSource.getFor(", parametrized(IResultGetter.class, p.type));
				if(p.type instanceof ParameterizedTypeName){
					ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)p.type;
					constr.addCode("$T.class",parameterizedTypeName.rawType);
					for(TypeName ta: parameterizedTypeName.typeArguments){
						constr.addCode(",$T.class",ta);					
					}
					constr.addCode(");\n");
				}else{
					constr.addCode("$T.class);\n", p.type.box());
				}
//				constr.addCode("\n");
			}
		}
		
		cp.addMethod(constr.build());

		// public static final Class<SampleEntity> ENTITY_CLASS = SampleEntity.class;
		addField(cp, PUBLIC().STATIC().FINAL(), parametrized(Class.class, def.type), "ENTITY_CLASS", "$T.class",def.type);	
		
		// public static final Class<SampleEntityEnum> ENTITY_ENUM = SampleEntityEnum.class;
		addField(cp, PUBLIC().STATIC().FINAL(), parametrized(Class.class, def.typeEnum), "ENTITY_ENUM", "$T.class",def.typeEnum);
		
		// public static final String TABLE_NAME = "sample_table";
		addField(cp, PUBLIC().STATIC().FINAL(), String.class, "TABLE_NAME", "$S",def.tableName);

		add_entityValues(cp, def);		
		add_makeNew(cp, def);
		add_fromResultSet(cp, def);

//		TypeName returnType = parametrized(EnumArrayUpdateDelta.class,def.typeEnum);
//		addMethod(cp,PUBLIC().STATIC().FINAL(), returnType, "delta", delta->{
//			delta.addParameter(long.class, "changeSet");
//			delta.addParameter(ArrayTypeName.of(Object.class), "values");
//			delta.addCode("return new $T(changeSet, values, $T.COLUMN_ARRAY);\n", returnType,def.typeEnum);			
//		});
		
		//@Override
		//public final Class<Sample> getEntityClass(){ return ENTITY_CLASS; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(Class.class, def.type), "getEntityClass", method->{
			method.addAnnotation(Override.class);
			method.addCode("return ENTITY_CLASS;\n");
		});
		//@Override
		//public final Class<SampleEnum> getEntityEnum(){ return ENTITY_ENUM; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(Class.class, def.typeEnum), "getEntityEnum", method->{
			method.addAnnotation(Override.class);
			method.addCode("return ENTITY_ENUM;\n");
		});
		//@Override
		//public final String getTableName(){ return TABLE_NAME; }
		addMethod(cp,PUBLIC().FINAL(), String.class, "getTableName", method->{
			method.addAnnotation(Override.class);
			method.addCode("return TABLE_NAME;\n");
		});		
		//@Override
		//public final int getColumnCount(){ return 3; }
		addMethod(cp,PUBLIC().FINAL(), int.class, "getColumnCount", method->{
			method.addAnnotation(Override.class);
			method.addCode("return "+def.getProps().size()+";\n");
		});
		//@Override
		//public final String getColumnNamesStr(){ return COLUMNS_STR; }
		addMethod(cp,PUBLIC().FINAL(), String.class, "getColumnNamesStr", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.COLUMNS_STR;\n",def.typeEnum);
		});		
		//@Override
		//public final String getColumnNames(){ return COLUMN_NAMES; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(ImmutableList.class,String.class), "getColumnNames", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.COLUMN_NAMES;\n",def.typeEnum);
		});		
		//@Override
		//public final String getColumns(){ return COLUMNS; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(ImmutableList.class,def.typeEnum), "getColumns", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.COLUMNS;\n",def.typeEnum);
		});		
		//@Override
		//public final String getPrimaryColumn(){ return COLUMNS_STR; }
		addMethod(cp,PUBLIC().FINAL(), def.typeEnum, "getPrimaryColumn", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.PRIMARY;\n",def.typeEnum);
		});			
		//@Override
		//public final SampleEnum getColumn(String name){ return SamleEnum.valueOf(name); }
		addMethod(cp,PUBLIC().FINAL(), def.typeEnum, "getColumn", method->{
			method.addParameter(String.class, "name");
			method.addAnnotation(Override.class);
			method.addCode("return $T.valueOf(name);\n",def.typeEnum);
		});
		//@Override
		//public final SampleEnum getColumn(String name){ return COLUMN_ARRAY[ordinal]; }
		addMethod(cp,PUBLIC().FINAL(), def.typeEnum, "getColumn", method->{
			method.addParameter(int.class, "ordinal");
			method.addAnnotation(Override.class);
			method.addCode("return $T.COLUMN_ARRAY[ordinal];\n",def.typeEnum);
		});	
		
		if(primaryProp == null){
			//@Override
			//public final Object entityGetPrimary(Sample instance){ return null; }
			addMethod(cp,PUBLIC().FINAL(), Object.class, "entityGetPrimary", method->{
				method.addParameter(def.type, "instance");
				method.addAnnotation(Override.class);
				method.addCode("return null;\n");
			});
		}else{
			//@Override
			//public final Long entityGetPrimary(Sample instance){ return instance.getId(); }
			addMethod(cp,PUBLIC().FINAL(), primaryProp.type, "entityGetPrimary", method->{
				method.addParameter(def.type, "instance");
				method.addAnnotation(Override.class);
				method.addCode("return instance."+primaryProp.getterName+"();");
			});
		}

		//@Override
		//public final Object[] entityGetValues(Sample instance){return entityValues(instance); }
		addMethod(cp,PUBLIC().FINAL(), ArrayTypeName.of(Object.class), "entityGetValues", method->{
			method.addParameter(def.type, "instance");
			method.addAnnotation(Override.class);
			method.addCode("return entityValues(instance);");
		});

		//@Override
		//public final Sample entityFromValues(Object[] arr){return makeNew(arr); }
		addMethod(cp,PUBLIC().FINAL(), def.type, "entityFromValues", method->{
			method.addParameter(ArrayTypeName.of(Object.class), "arr");
			method.addAnnotation(Override.class);
			method.addCode("return makeNew(arr);");
		});
		
		return cp;
	}

	private void add_makeNew(TypeSpec.Builder cp, EntityDef def) {
		MethodSpec.Builder makeNew = methodBuilder(PUBLIC().STATIC().FINAL(), def.typeImmutable, "makeNew");
		makeNew.addParameter(ArrayTypeName.of(Object.class), "v");
		
		makeNew.addCode("if(v == null) return null;\n\n");
		
		makeNew.addCode("return new $T(\n",def.typeImmutable);
		
		int count = def.getProps().size();
		for(int i=0; i<count; i++) {
			Property prop = def.getProps().get(i);
			makeNew.addCode("\t($T)v[$L]",prop.type.box(),i);
			makeNew.addCode(i == count-1 ? "":",");
			makeNew.addCode(" //"+prop.fieldName+"\n");
		}
		makeNew.addCode(");\n");
		cp.addMethod(makeNew.build());
		
	}
	private void add_entityValues(TypeSpec.Builder cp, EntityDef def) {
		MethodSpec.Builder entityValues = methodBuilder(PUBLIC().STATIC().FINAL(), ArrayTypeName.of(Object.class), "entityValues");

		addParameter(entityValues,def.type, "v");
		
		entityValues.addCode("if(v instanceof $T)", IEntityValues.class);
		entityValues.addCode(" return (($T)v).getEntityValues();\n", IEntityValues.class);

		entityValues.addCode("return new  Object[]{\n");

		int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property property = def.getProps().get(i);
        	entityValues.addCode("\tv."+property.getterName+"()"+(i == count-1 ? "":",")+"\n");

        }
		entityValues.addCode("};\n");
		cp.addMethod(entityValues.build());
		
	}
	
	private String getterName(Property p){
		if(isType(p, "int","java.lang.Integer")){
			return "getInt";
		
		}else if(isType(p, "boolean","java.lang.Boolean")){
			return "getBoolean";
		
		}else if(isType(p, "long","java.lang.Long")){
			return "getLong";
			
		}else if(isType(p, "double","java.lang.Double")){
			return "getDouble";
			
		}else if(isType(p, "float","java.lang.Float")){
			return "getDouble";

		}else if(isType(p, "java.lang.String")){
			return "getString";
		}

		return null;
	}
	
	private void add_fromResultSet(TypeSpec.Builder cp, EntityDef def) {
		MethodSpec.Builder method = methodBuilder(PUBLIC().FINAL(), def.type, "fromResultSet");
		
		method.addParameter(ResultSet.class, "rs");
		method.addException(java.sql.SQLException.class);

		CodeBlock.Builder returnValue = CodeBlock.builder().add("return new $T(\n",def.typeImmutable);

		int i=1;
		for(Property p:def.getProps()) {
			method.addCode("$T $L",p.type, p.fieldName);
			
			String getter = getterName(p);
			
			if(getter == null){
				method.addCode(" = ($T)_$L_resultGetter.get(rs,$L);\n",p.type, p.fieldName, i);
			}else{
				method.addCode(" = rs."+getter+"("+i+");\n");
			}
			
			// add to constructor
			if(i>1) returnValue.add(", ");
			returnValue.add(p.fieldName);			

			i++;
		}
		
		returnValue.add(");");
		
		method.addCode("\n");
		method.addCode(returnValue.build());
		
		cp.addMethod(method.build());
	}
}
