package hr.hrg.hipster.processor;

import static com.squareup.javapoet.TypeSpec.classBuilder;
import static hr.hrg.javapoet.PoetUtil.*;

import com.squareup.javapoet.*;

import hr.hrg.hipster.dao.*;
import hr.hrg.hipster.dao.change.*;
import hr.hrg.hipster.sql.*;

public class GenBuilder {

	private boolean jackson;
	private ClassName columnMetaBase;

	public GenBuilder(boolean jackson, ClassName columnMetaBase) {
		this.jackson = jackson;
		this.columnMetaBase = columnMetaBase;
	}


	public TypeSpec.Builder gen2(EntityDef def) {
		TypeSpec.Builder builder = classBuilder(def.typeBuilder);
		PUBLIC().to(builder);
        
		if(def.isInterface){			
			builder.addSuperinterface(def.type);
		}else{
			builder.superclass(def.type);
		}

		addInterfaces(def, builder, jackson, columnMetaBase);
        
        CodeBlock.Builder code = CodeBlock.builder().add("return new $T(", def.typeImmutable);

        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property prop = def.getProps().get(i);
			FieldSpec fieldSpec = addField(builder, PROTECTED(), prop.type, prop.name);
        	
			MethodSpec.Builder g = methodBuilder(PUBLIC(), prop.type, prop.getterName).addAnnotation(Override.class);
			g.addCode("return "+prop.fieldName+";\n");
			GenImmutable.copyAnnotations(g, prop);
			builder.addMethod(g.build());

			MethodSpec.Builder bm = methodBuilder(PUBLIC(), prop.type, prop.name).addAnnotation(Override.class);
			addSetterParameter(bm, fieldSpec,null);
			bm.addCode("return this;");
			
			code.add("\t\t"+prop.fieldName+(i == count-1 ? "":","));
        }
        
        genConstructors(def, builder, jackson);
        
        code.add("\t);");
		
        GenImmutable.addEnumGetter(def, builder,columnMetaBase);
        builder.addMethod(genEnumSetter(def, builder, columnMetaBase).build());
        if(jackson) GenImmutable.addDirectSerializer(def,builder);
        
        return builder;
	}

	public static void addInterfaces(EntityDef def, TypeSpec.Builder builder, boolean jackson, ClassName columnMetaBase) {
		// builder.addSuperinterface(parametrized(IEnumGetter.class, columnMetaBase));
		builder.addSuperinterface(IUpdatable.class); // includes IEnumGetter (IUpdatable extends it)  
	}	
	
	public static void genConstructors(EntityDef def, TypeSpec.Builder builder, boolean jackson){
        // empty default constructor
		addconstructor(builder, null);

        GenImmutable.genConstructor(def,builder,jackson);
	}

	
	public static MethodSpec.Builder genEnumSetter(EntityDef def, TypeSpec.Builder cp, ClassName columnMetaBase){
		TypeVariableName typeT = TypeVariableName.get("T");
		ParameterizedTypeName typeKEy = parametrized(Key.class, typeT);

        MethodSpec.Builder setValue = methodBuilder(PUBLIC(), void.class, "setValue");
        setValue.addAnnotation(Override.class);
        setValue.addTypeVariable(typeT);
        setValue.addParameter(typeKEy, "column");
        setValue.addParameter(typeT, "value");
        setValue.addCode("this.setValue(column.ordinal(), value);\n");
		cp.addMethod(setValue.build());
		
        setValue = methodBuilder(PUBLIC(), void.class, "setValue");
        setValue.addAnnotation(Override.class);
        setValue.addParameter(int.class, "ordinal");
        setValue.addParameter(Object.class, "value");
        setValue.addCode("switch (ordinal) {\n");

        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property prop = def.getProps().get(i);
        	setValue.addCode("case "+i+": this."+prop.fieldName+" = ($T) value;break;\n",prop.type.box());
        }
        setValue.addCode("default: throw new ArrayIndexOutOfBoundsException(ordinal);\n");
        setValue.addCode("}\n");
		
        return setValue;
	}	
	
}
