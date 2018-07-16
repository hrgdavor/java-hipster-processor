package hr.hrg.hipster.processor;

import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static hr.hrg.hipster.processor.HipsterProcessorUtil.*;
import static hr.hrg.javapoet.PoetUtil.*;

import java.sql.*;
import java.util.*;
import java.util.Map.*;

import com.squareup.javapoet.*;
import com.squareup.javapoet.FieldSpec.*;

import hr.hrg.hipster.*;
import hr.hrg.hipster.dao.*;
import hr.hrg.hipster.dao.change.*;
import hr.hrg.hipster.sql.*;
import hr.hrg.javapoet.*;


public class GenMeta {

	public TypeSpec.Builder gen(EntityDef def, ClassName columnMetaBase) {
		
		TypeSpec.Builder cp = classBuilder(PUBLIC(), def.typeMeta);

		Property primaryProp = def.getPrimaryProp();
		TypeName primaryType = primaryProp == null ? TypeName.get(Object.class) : primaryProp.type;
	
		cp.superclass(parametrized(BaseEntityMeta.class,def.type, primaryType, columnMetaBase));
		
		// public static final Class<SampleEntity> ENTITY_CLASS = SampleEntity.class;
		addField(cp, PRIVATE().STATIC().FINAL(), parametrized(Class.class, def.type), "ENTITY_CLASS", "$T.class",def.type);	
		
		// public static final String TABLE_NAME = "sample_table";
		addField(cp, PUBLIC().STATIC().FINAL(), String.class, "TABLE_NAME", "$S",def.tableName);
		
		// public static final String TABLE_NAME = "sample_table";
		addField(cp, PUBLIC().STATIC().FINAL(), QueryLiteral.class, "TABLE", "new $T(TABLE_NAME,true)",QueryLiteral.class);
		
		int ordinal = 0;
		for(Property prop: def.getProps()){
			// type or raw type
			TypeName rawType = prop.type;
			if(prop.type instanceof ParameterizedTypeName){				
				ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)prop.type;
				rawType = parameterizedTypeName.rawType;
			}
			
			com.squareup.javapoet.CodeBlock.Builder codeBlock = CodeBlock.builder();
			codeBlock.add("new $T<$T>($L, $S",columnMetaBase, rawType.box(), ordinal, prop.name);
			codeBlock.add(",$S",prop.columnName);
			codeBlock.add(",$S",prop.getterName);
			codeBlock.add(",ENTITY_CLASS");
			
			
			codeBlock.add(",$T.class",rawType.box());

			if(prop.isPrimitive())
				codeBlock.add(",$T.class",prop.type);
			else
				codeBlock.add(",null");
			
			codeBlock.add(",TABLE_NAME");
			codeBlock.add(",$S",prop.sql);

			// type parameters if any
			if(prop.type instanceof ParameterizedTypeName){				
				ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)prop.type;
				for(TypeName ta: parameterizedTypeName.typeArguments){
					codeBlock.add(",$T.class",ta);					
				}
			}
			codeBlock.add(")");
			
			if(prop.annotations.size() == 0) {
				// CASE: Reflection
				// force initialisation of "annotations" field in BaseColumnMeta to empty array, 
				// (we know there ar non, so reflection can be skipped for this one)
				codeBlock.add(".withAnnotations()");
				

				// CASE: Generated (no reflection)
				// - comment line above 
				// - make sure "annotations" field in BaseColumnMeta is initialised to: new Annotation[0]
				
			}else if(prop.annotations.size() > 0) {
				// CASE: Generated (no reflection)
				// - uncomment all lines in this block

//				codeBlock.add(".withAnnotations(");
//				int i=0;
//				codeBlock.indent();
//				codeBlock.indent();
//				for(AnnotationSpec spec: prop.annotations) {
//					if(i>0) codeBlock.add(", ");
//					
//					codeBlock.add("\nannotation($T.class", spec.type);
//					int j=0;
//					for(Entry<String, List<CodeBlock>> elem:spec.members.entrySet()) {
//						codeBlock.add(", ");
//						
//						codeBlock.add("$S,",elem.getKey());
//						if(elem.getValue().size() == 1) {
//							codeBlock.add(elem.getValue().get(0));							
//						}else {
//							codeBlock.add("new Object[]{");
//							for(int k=0; k<elem.getValue().size(); k++) {
//								if(k>0) codeBlock.add(", ");
//								codeBlock.add(elem.getValue().get(k));
//							}
//							codeBlock.add("}");
//						}
//						j++;
//					}
//					codeBlock.add(")");
//					i++;
//				}
//				codeBlock.add(")");				
//				codeBlock.unindent();
//				codeBlock.unindent();

			}

			addField(cp, PUBLIC().STATIC().FINAL(), parametrized(columnMetaBase, rawType.box()), prop.fieldName, new FieldCustomizer() {
				@Override
				public void customize(Builder arg0) {
					arg0.initializer(codeBlock.build());
				}
			});	
			ordinal++;
		}

		if(primaryProp == null) {
			addField(cp, PUBLIC().STATIC().FINAL(), columnMetaBase, "PRIMARY", "null");
			
		}else {
			addField(cp, PUBLIC().STATIC().FINAL(), parametrized(columnMetaBase, def.getPrimaryProp().type), "PRIMARY", primaryProp.name);			
		}
				
		addColumnsDef(cp, def, columnMetaBase);
		
		FieldSpec ordinalField = addField(cp,PRIVATE().FINAL(), int.class, "ordinal");
		
//		for(Property p:def.getProps()) {
//			if(getterName(p) == null){
//				addField(cp,PUBLIC().FINAL(), parametrized(ICustomType.class, p.type), "_"+p.fieldName+"_typeHandler");
//			}
//		}

		MethodSpec.Builder constr = constructorBuilder(PUBLIC());
		addParameter(constr, TypeSource.class, "_typeSource");
		constr.addCode("super(ordinal, TABLE_NAME, COLUMN_COUNT);\n");
		addSetterParameter(constr, ordinalField, null);
		int i=0;
		
		CodeBlock.Builder gettersBlock = CodeBlock.builder().indent();
		boolean hasGetters = false;
		
		for(Property p:def.getProps()) {
			ParameterizedTypeName parametrizedCustomType = parametrized(ICustomType.class, p.type.box());
			if(p.customType != null) {
				gettersBlock.add("_typeHandler["+i+"] = ($T) _typeSource.getInstanceRequired($T.class);\n", parametrizedCustomType, p.customType);				
			}else if(!p.customTypeKey.isEmpty()){
				gettersBlock.add("_typeHandler["+i+"] = ($T) _typeSource.getNamedRequired($S);\n", parametrizedCustomType, p.customTypeKey);
			}else {				
				gettersBlock.add("_typeHandler["+i+"] = ($T) _typeSource.getForRequired(", parametrizedCustomType);
				hasGetters = true;
				if(p.type instanceof ParameterizedTypeName){
					ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)p.type;
					gettersBlock.add("$T.class",parameterizedTypeName.rawType);
					for(TypeName ta: parameterizedTypeName.typeArguments){
						gettersBlock.add(",$T.class",ta);					
					}
					gettersBlock.add(");\n");
				}else{
					gettersBlock.add("$T.class);\n", p.type.box());
				}
			}
			
			i++;
		}
		gettersBlock.unindent();
		
		constr.addCode(gettersBlock.build());
		
//		addconstructor(cp, PUBLIC(), method-> {
//			method.addParameter(HipsterSql.class, "hipster");
//			method.addCode("this(hipster.getGetterSource());\n");
//			method.addCode("hipster.getReaderSource().registerFor(this, $T.class);\n",def.type);
//		});
		
		cp.addMethod(constr.build());
		
		add_fromResultSet(cp, def);

//		TypeName returnType = parametrized(EnumArrayUpdateDelta.class,columnMetaBase);
//		addMethod(cp,PRIVATE().STATIC().FINAL(), returnType, "delta", delta->{
//			delta.addParameter(long.class, "changeSet");
//			delta.addParameter(ArrayTypeName.of(Object.class), "values");
//			delta.addCode("return new $T(changeSet, values, $T.COLUMN_ARRAY);\n", returnType,columnMetaBase);			
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
			//public final IUpdatable<BaseColumnMeta> mutableCopy(Sample v){ throw new RuntimeExcep(v); }
			addMethod(cp,PUBLIC().FINAL(), parametrized(IUpdatable.class, columnMetaBase), "mutableCopy", method->{
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
		//public final String getColumnNamesStr(){ return EntityEnum.COLUMNS_STR; }
		addMethod(cp,PUBLIC().FINAL(), String.class, "getColumnNamesStr", method->{
			method.addAnnotation(Override.class);
			method.addCode("return COLUMNS_STR;\n");
		});

		//public final String getColumnNames(){ return EntityEnum.COLUMN_NAMES; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(ImmutableList.class,String.class), "getColumnNames", method->{
			method.addCode("return COLUMN_NAMES;\n");
		});		

		//@Override
		//public final boolean containsColumn(){ return EntityEnum.COLUMN_NAMES.contains(columnName); }
		addMethod(cp,PUBLIC().FINAL(), boolean.class, "containsColumn", method->{
			addParameter(method, String.class, "columnName");
			method.addCode("return $T.binarySearch(COLUMN_ARRAY_SORTED_STR, columnName) > -1;\n",Arrays.class);
		});		
		
		//@Override
		//public final String getColumns(){ return COLUMNS; }
		addMethod(cp,PUBLIC().FINAL(), parametrized(ImmutableList.class,columnMetaBase), "getColumns", method->{
			method.addAnnotation(Override.class);
			method.addCode("return COLUMNS;\n");
		});		
		//@Override
		//public final String getPrimaryColumn(){ return COLUMNS_STR; }
		addMethod(cp,PUBLIC().FINAL(), columnMetaBase, "getPrimaryColumn", method->{
			method.addAnnotation(Override.class);
			method.addCode("return PRIMARY;\n");
		});			
		//@Override
		//public final SampleEnum getColumn(String name){ return SamleEnum.valueOf(name); }
		addMethod(cp,PUBLIC().FINAL(), columnMetaBase, "getColumn", method->{
			method.addParameter(String.class, "columnName");
			method.addAnnotation(Override.class);
			method.addCode("int pos = $T.binarySearch(COLUMN_ARRAY_SORTED_STR, columnName);\n",Arrays.class);
			method.addCode("return pos < 0 ? null: COLUMN_ARRAY_SORTED[pos];\n");
		});
		
		//@Override
		//public final SampleEnum getColumn(String name){ return COLUMN_ARRAY[ordinal]; }
		addMethod(cp,PUBLIC().FINAL(), columnMetaBase, "getColumn", method->{
			method.addParameter(int.class, "ordinal");
			method.addAnnotation(Override.class);
			method.addCode("return COLUMN_ARRAY[ordinal];\n");
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
				method.addCode("return instance."+primaryProp.getterName+"();\n");
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
				method.addCode(" = ($T)_typeHandler[$L].get(rs,$L);\n",p.type,i-1, i);
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
	
	private void addColumnsDef(TypeSpec.Builder cp, EntityDef def, ClassName columnMetaBase) {
		List<String> colNames = new ArrayList<>();
		List<String> enumNames = new ArrayList<>();
		for(Property p:def.props) {
			colNames.add(p.columnName);
			enumNames.add(p.fieldName);
		}

		final StringBuffer arrStr = new StringBuffer("(");
		final StringBuffer str = new StringBuffer("");

		String delim = "";
		for (String col : colNames) {
			arrStr.append(delim); str.append(delim);
			arrStr.append("\"").append(col).append("\"");
			str.append('"').append(col).append('"');
			delim = ",";
		}
		arrStr.append(")");

		final StringBuffer arr = new StringBuffer("{")  ;
		delim = "";
		for (String col : enumNames) {
			arr.append(delim);
			arr.append(col);
			delim = ",";
		}

		arr.append("}");
		
		addField(cp,PRIVATE().STATIC().FINAL(), String.class, "COLUMNS_STR", 
				field->field.initializer("$S",str.toString()));
		
		addField(cp,PUBLIC().STATIC().FINAL(), int.class, "COLUMN_COUNT", 
				field->field.initializer(""+def.props.size()));
		
		addField(cp,PRIVATE().STATIC().FINAL(), parametrized(ImmutableList.class, String.class), "COLUMN_NAMES", 
				field->field.initializer("ImmutableList.safe"+arrStr.toString()));
		
		addField(cp,PRIVATE().STATIC().FINAL(), ArrayTypeName.of(columnMetaBase), "COLUMN_ARRAY", 
				field->field.initializer(arr.toString()));

		addField(cp,PUBLIC().STATIC().FINAL(), parametrized(ImmutableList.class, columnMetaBase), "COLUMNS", 
				field->field.initializer("ImmutableList.safe(COLUMN_ARRAY)"));

		Collections.sort(enumNames);

		arr.setLength(0);
		str.setLength(0);
		arr.append("{");
		str.append("{");

		delim = "";
		for (String col : enumNames) {
			arr.append(delim); str.append(delim);
			arr.append(col);
			str.append('"').append(col).append('"');
			delim = ",";
		}
		arr.append("}");
		str.append("}");

		addField(cp,PRIVATE().STATIC().FINAL(), ArrayTypeName.of(columnMetaBase), "COLUMN_ARRAY_SORTED", 
				field->field.initializer(arr.toString()));
		
		addField(cp,PRIVATE().STATIC().FINAL(), ArrayTypeName.of(String.class), "COLUMN_ARRAY_SORTED_STR", 
				field->field.initializer(str.toString()));

	}	
}
