package hr.hrg.hipster.processor;

import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.enumBuilder;
import static hr.hrg.javapoet.PoetUtil.*;

import java.io.*;
import java.util.*;

import com.squareup.javapoet.*;
import com.squareup.javapoet.TypeSpec.*;

import hr.hrg.hipster.sql.*;
import hr.hrg.javapoet.*;

public class GenEnum {

	public TypeSpec.Builder gen2(EntityDef def) throws IOException {
		
		Builder enumbuilder = enumBuilder(def.typeEnum)
				.addSuperinterface(IColumnMeta.class)
				.addSuperinterface(IQueryLiteral.class);

		PUBLIC().to(enumbuilder);
		
		String initCode = def.getPrimaryProp() == null ? "null":def.getPrimaryProp().fieldName;
		addField(enumbuilder, PUBLIC().STATIC().FINAL(), def.typeEnum, "PRIMARY", initCode);
		
		BeanCustomizer addOverride = (field, getter, setter) -> {
			getter.addAnnotation(Override.class);
		};
		
		addBeanfieldReadonly(enumbuilder, TN_CLASS_Q, "_type", addOverride);
		addBeanfieldReadonly(enumbuilder, boolean.class, "_primitive", addOverride);
		addBeanfieldReadonly(enumbuilder, String.class, "_columnName", addOverride);
		addBeanfieldReadonly(enumbuilder, String.class, "_getterName", addOverride);
		addBeanfieldReadonly(enumbuilder, String.class, "_tableName", addOverride);
		addBeanfieldReadonly(enumbuilder, String.class, "_columnSql", addOverride);
		addBeanfieldReadonly(enumbuilder, parametrized(ImmutableList.class, TN_CLASS_Q), "_typeParams", addOverride);
		
		for(Property prop: def.getProps()){
			com.squareup.javapoet.CodeBlock.Builder codeBlock = CodeBlock.builder().add("$S",prop.columnName);
			codeBlock.add(",$S",prop.getterName);
			
			// type or raw type
			if(prop.type instanceof ParameterizedTypeName){				
				ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)prop.type;
				codeBlock.add(",$T.class",parameterizedTypeName.rawType);
			}else{
				codeBlock.add(",$T.class",prop.type);
			}

			// regular arguments
			codeBlock.add(",$L",prop.isPrimitive());
			codeBlock.add(",$S",prop.tableName);
			codeBlock.add(",$S",prop.sql);

			// type parameters if any
			if(prop.type instanceof ParameterizedTypeName){				
				ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName)prop.type;
				for(TypeName ta: parameterizedTypeName.typeArguments){
					codeBlock.add(",$T.class",ta);					
				}
			}
			
			TypeSpec spec = anonymousClassBuilder(codeBlock.build()
//					"$S,$T.class,$L", 
//					prop.columnName, 
//					prop.type,
//					prop.isPrimitive()
					).build();
			
			enumbuilder.addEnumConstant(prop.fieldName, spec);
		}
				
		addconstructor(enumbuilder, PRIVATE(), (method) -> {
			addSetterParameter(enumbuilder,method,"_columnName","_getterName","_type","_primitive","_tableName","_columnSql");
			method.addParameter(ArrayTypeName.of(TN_CLASS_Q), "typeParams");
			method.varargs();
			method.addCode("this._typeParams = $T.safe(typeParams);\n",ImmutableList.class);
		});

		addColumnsDef(enumbuilder,def);
		
		addMethod(enumbuilder, PUBLIC(), boolean.class, "isGeneric", method->{
			method.addAnnotation(Override.class);
			method.addCode("return _typeParams.isEmpty();\n");
		});

		addMethod(enumbuilder, PUBLIC(), String.class, "getQueryText", method->{
			method.addAnnotation(Override.class);
			method.addCode("return _columnName;\n");
		});
		
		addMethod(enumbuilder, PUBLIC().FINAL(), String.class, "toString", method->{
			method.addAnnotation(Override.class);
			method.addCode("return _columnName;\n");
		});

		addMethod(enumbuilder, PUBLIC(), TN_CLASS_Q, "getEntity", method->{
			method.addAnnotation(Override.class);
			method.addCode("return $T.class;\n", def.type);
		});	
		
		return enumbuilder;
	}

	private void addColumnsDef(TypeSpec.Builder cp, EntityDef def) {
		List<String> colNames = new ArrayList<>();
		for(Property p:def.props) {
			colNames.add(p.columnName);
		}

		StringBuffer arr = new StringBuffer("(");
		StringBuffer str = new StringBuffer("");

		String delim = "";
		for (String col : colNames) {
			arr.append(delim); str.append(delim);
			arr.append("\"").append(col).append("\"");
			str.append('"').append(col).append('"');
			delim = ",";
		}

		arr.append(")");
		
		addField(cp,PUBLIC().STATIC().FINAL(), String.class, "COLUMNS_STR", 
				field->field.initializer("$S",str.toString()));
		
		addField(cp,PUBLIC().STATIC().FINAL(), int.class, "COLUMN_COUNT", 
				field->field.initializer(""+def.props.size()));
		
		addField(cp,PUBLIC().STATIC().FINAL(), parametrized(ImmutableList.class, String.class), "COLUMN_NAMES", 
				field->field.initializer("ImmutableList.safe"+arr.toString()));
		
		addField(cp,PUBLIC().STATIC().FINAL(), ArrayTypeName.of(def.typeEnum), "COLUMN_ARRAY", 
				field->field.initializer("$T.values()",def.typeEnum));

		addField(cp,PUBLIC().STATIC().FINAL(), parametrized(ImmutableList.class, def.typeEnum), "COLUMNS", 
				field->field.initializer("ImmutableList.safe(COLUMN_ARRAY);"));
		
	}
	
}
