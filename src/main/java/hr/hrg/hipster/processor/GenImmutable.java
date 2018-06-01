package hr.hrg.hipster.processor;

import static com.squareup.javapoet.TypeSpec.classBuilder;
import static hr.hrg.hipster.processor.HipsterProcessorUtil.*;
import static hr.hrg.javapoet.PoetUtil.*;

import java.io.*;

import com.squareup.javapoet.*;
import com.squareup.javapoet.MethodSpec.Builder;

import hr.hrg.hipster.dao.*;
import hr.hrg.hipster.dao.jackson.*;
import hr.hrg.hipster.sql.*;

public class GenImmutable {

	private boolean jackson;
	private ClassName columnMetaBase;

	public GenImmutable(boolean jackson, ClassName columnMetaBase) {
		this.jackson = jackson;
		this.columnMetaBase = columnMetaBase;
	}

	public TypeSpec.Builder gen2(EntityDef def) throws IOException {
		TypeSpec.Builder builder = classBuilder(def.typeImmutable);
		PUBLIC().FINAL().to(builder);
		
		if(def.isInterface){
			builder.addSuperinterface(def.type);			
		}else{
			builder.superclass(def.type);						
		}
		builder.addSuperinterface(parametrized(IEnumGetter.class, BaseColumnMeta.class));		

        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property prop = def.getProps().get(i);
			addField(builder, PRIVATE().FINAL(), prop.type, prop.name);
        	
			MethodSpec.Builder g = methodBuilder(PUBLIC(), prop.type, prop.getterName).addAnnotation(Override.class);
			if(prop.jsonIgnore) g.addAnnotation(CN_JsonIgnore);
			g.addCode("return "+prop.fieldName+";\n");

			builder.addMethod(g.build());
			
        }
       
        addEnumGetter(def, builder, columnMetaBase);
        genConstructor(def,builder,jackson, false);
        
		if(jackson) addDirectSerializer(def,builder);
        
		return builder;
	}

	public static void addDirectSerializer(EntityDef def, TypeSpec.Builder builder){
		builder.addAnnotation(annotationSpec(CN_JsonSerialize, "using", "$T.class", DirectSerializer.class));
		builder.addSuperinterface(IDirectSerializerReady.class);

		addMethod(builder, PUBLIC(), void.class, "serialize", method -> {		
			addParameter(method, CN_JsonGenerator, "jgen");
			addParameter(method, CN_SerializerProvider, "provider");
		
			method.addException(IOException.class);
			method.addException(CN_JsonGenerationException);
			
			method.addCode("jgen.writeStartObject();\n\n");
			
			int count = def.getProps().size();
			for (int i = 0; i < count; i++) {
				Property prop = def.getProps().get(i);
				
				if(prop.jsonIgnore) continue;
				
				String typeStr = prop.type.toString();
				boolean primitive = prop.type.isPrimitive();
				
				method.addCode("jgen.writeFieldName($S);\n",prop.name);

				if(primitive || prop.type.isBoxedPrimitive()){
					TypeName unboxed = prop.type.unbox();
					if(TypeName.INT.equals(unboxed) 
							|| TypeName.LONG.equals(unboxed)
							|| TypeName.FLOAT.equals(unboxed)
							|| TypeName.DOUBLE.equals(unboxed)
							|| TypeName.SHORT.equals(unboxed)
							|| TypeName.BYTE.equals(unboxed)
							){
						addWrite(method, "writeNumber", prop.name, !primitive);
					}else if(TypeName.BOOLEAN.equals(unboxed)){
						addWrite(method, "writeBoolean", prop.name, !primitive);						
					}else if(TypeName.CHAR.equals(unboxed)){
						if(!primitive){
							method.addCode("if ($L == null)\n",prop.name);
							method.addCode("\tjgen.writeNull();\n");
							method.addCode("else\n");
							method.addCode("\t");
						}
						method.addCode("jgen.writeString(new char[]{$L},0,1);\n",prop.name);
					}
				} else if(typeStr.equals("java.lang.String")){
					addWrite(method, "writeString", prop.name, true);
				} else {
					addWrite(method, "writeObject", prop.name, true);
				}
				method.addCode("\n");
			}
			
			method.addCode("jgen.writeEndObject();\n");
			
		});
	}

	public static void addWrite(Builder method, String jgenMethod, String prop, boolean nullCheck) {
		if(!nullCheck)
			method.addCode("jgen.$L($L);\n",jgenMethod,prop);
		else{
			method.addCode("if ($L == null)\n",prop);
			method.addCode("\tjgen.writeNull();\n");
			method.addCode("else\n");
			method.addCode("\tjgen.$L($L);\n",jgenMethod,prop);
		}
	}
	public static void addEnumGetter(EntityDef def, TypeSpec.Builder cp,ClassName columnMetaBase){

		
		addMethod(cp, Object.class, "getValue", method -> {		
			PUBLIC().FINAL().to(method);
	        method.addAnnotation(Override.class);
	        addParameter(method, columnMetaBase	, "column");
	        method.addCode("return this.getValue(column.ordinal());\n");
		});
		
		addMethod(cp, Object.class, "getValue", method -> {
			PUBLIC().FINAL().to(method);
			method.addAnnotation(Override.class);
			addParameter(method, int.class, "ordinal");
			method.addCode("switch (ordinal) {\n");

			int count = def.getProps().size();
			for (int i = 0; i < count; i++) {
				Property prop = def.getProps().get(i);
				method.addCode("case " + i + ": return this." + prop.fieldName + ";\n");

			}
			method.addCode("default: throw new ArrayIndexOutOfBoundsException(ordinal);\n");
			method.addCode("}\n");
		});
	}

	public static void genConstructor(EntityDef def, TypeSpec.Builder cp, boolean jackson, boolean mutable){

        MethodSpec.Builder constr = constructorBuilder(PUBLIC());
        if(jackson) constr.addAnnotation(CN_JsonCreator);
		
        MethodSpec.Builder constr2 = constructorBuilder(PUBLIC());
        addParameter(constr2,def.type, "v");
        
        if(mutable){
            constr2.addCode("if(v == null) return;\n");
        }
        
        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property property = def.getProps().get(i);

        	addSetterParameter(constr, property.type, property.name, param->{
        		if(jackson)
        			param.addAnnotation(annotationSpec(CN_JsonProperty,"value", "$S",property.name));
        	});
        
        	constr2.addCode("this."+property.name+" = v."+property.getterName +"();\n");
        }
        cp.addMethod(constr.build());
        cp.addMethod(constr2.build());
	}
	
}
