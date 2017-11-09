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

		addconstructor(cp, PUBLIC(), method-> {
			method.addParameter(IHipsterConnection.class, "conn");
			method.addCode("this(conn.getHipster().getGetterSource());\n");
			method.addCode("conn.getHipster().getReaderSource().registerFor(this, $T.class);\n",def.type);
		});
		
		cp.addMethod(constr.build());

		// public static final Class<SampleEntity> ENTITY_CLASS = SampleEntity.class;
		addField(cp, PUBLIC().STATIC().FINAL(), parametrized(Class.class, def.type), "ENTITY_CLASS", "$T.class",def.type);	
		
		// public static final Class<SampleEntityEnum> ENTITY_ENUM = SampleEntityEnum.class;
		addField(cp, PUBLIC().STATIC().FINAL(), parametrized(Class.class, def.typeEnum), "ENTITY_ENUM", "$T.class",def.typeEnum);
		
		// public static final String TABLE_NAME = "sample_table";
		addField(cp, PUBLIC().STATIC().FINAL(), String.class, "TABLE_NAME", "$S",def.tableName);

		add_fromResultSet(cp, def);

//		TypeName returnType = parametrized(EnumArrayUpdateDelta.class,def.typeEnum);
//		addMethod(cp,PUBLIC().STATIC().FINAL(), returnType, "delta", delta->{
//			delta.addParameter(long.class, "changeSet");
//			delta.addParameter(ArrayTypeName.of(Object.class), "values");
//			delta.addCode("return new $T(changeSet, values, $T.COLUMN_ARRAY);\n", returnType,def.typeEnum);			
//		});
		
		if(def.genUpdate){			
			//@Override
			//public final SampleUpdate mutableCopy(Object v){ return new SmapleUpdate((Sample)v); }
			addMethod(cp,PUBLIC().FINAL(), def.typeUpdate, "mutableCopy", method->{
				method.addAnnotation(Override.class);
				addParameter(method, Object.class, "v");
				method.addCode("return new $T(($T)v);\n", def.typeUpdate, def.type);
			});
		}else{
			//@Override
			//public final IUpdatable<IColumnMeta> mutableCopy(Sample v){ throw new RuntimeExcep(v); }
			addMethod(cp,PUBLIC().FINAL(), parametrized(IUpdatable.class, def.typeEnum), "mutableCopy", method->{
				method.addAnnotation(Override.class);
				addParameter(method, Object.class, "v");
				method.addCode("throw new $T($S);\n", RuntimeException.class,"can not be implemented without updater");
			});
			
		}

		//@Override
		//public final Class<Sample> getEntityClass(){ return ENTITY_CLASS; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(Class.class, def.type), "getEntityClass", method->{
			method.addAnnotation(Override.class);
			method.addCode("return ENTITY_CLASS;\n");
		});
		//@Override
		//public final String getEntityName(){ return "Sample"; }
		addMethod(cp,PUBLIC().FINAL(), String.class, "getEntityName", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $S;\n", def.simpleName);
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
		//public final String getColumnNamesStr(){ return EntityEnum.COLUMNS_STR; }
		addMethod(cp,PUBLIC().FINAL(), String.class, "getColumnNamesStr", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.COLUMNS_STR;\n",def.typeEnum);
		});

		//public final String getColumnNames(){ return EntityEnum.COLUMN_NAMES; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(ImmutableList.class,String.class), "getColumnNames", method->{
			method.addCode("return $T.COLUMN_NAMES;\n",def.typeEnum);
		});		

		//@Override
		//public final boolean containsColumn(){ return EntityEnum.COLUMN_NAMES.contains(columnName); }
		addMethod(cp,PUBLIC().FINAL(), boolean.class, "containsColumn", method->{
			addParameter(method, String.class, "columnName");
			method.addCode("return $T.COLUMN_NAMES.contains(columnName);\n",def.typeEnum);
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
		
		return cp;
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
