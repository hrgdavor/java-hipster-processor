package hr.hrg.hipster.processor;

import static com.squareup.javapoet.TypeSpec.classBuilder;
import static hr.hrg.hipster.processor.HipsterProcessorUtil.*;
import static hr.hrg.javapoet.PoetUtil.*;

import com.squareup.javapoet.*;

import hr.hrg.hipster.dao.*;

public class GenUpdate {

	private boolean jackson;
	private boolean genBuilder;
	private ClassName columnMetaBase;

	public GenUpdate(boolean jackson, boolean genBuilder, ClassName columnMetaBase) {
		this.jackson = jackson;
		this.genBuilder = genBuilder;
		this.columnMetaBase = columnMetaBase;
	}

	public TypeSpec.Builder gen2(EntityDef def) {

		TypeSpec.Builder cp = classBuilder(def.typeUpdate);
		PUBLIC().to(cp);
        
		if(genBuilder){
			cp.superclass(def.typeBuilder);			
		}else{
			cp.addSuperinterface(def.type);
			GenBuilder.addInterfaces(def, cp, jackson, columnMetaBase);
		}

    	addField(cp,PROTECTED(), long.class, "_changeSet");

        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property prop = def.getProps().get(i);
        	
        	if(!genBuilder)	addField(cp,PROTECTED(), prop.type, prop.name);

			MethodSpec.Builder g = methodBuilder(PUBLIC(), prop.type, prop.getterName);

			if(prop.jsonIgnore) g.addAnnotation(CN_JsonIgnore);
			
			g.addCode("return "+prop.fieldName+";\n");
			if(genBuilder) g.addAnnotation(Override.class);
			cp.addMethod(g.build());

			MethodSpec.Builder bm = methodBuilder(PUBLIC(), def.typeUpdate, prop.name);
			bm.addCode("this._changeSet |= "+(1L<<i)+"L;\n");
			addSetterParameter(bm, prop.type, prop.name, null);
			bm.addCode("return this;\n");
			cp.addMethod(bm.build());
        }
        
        MethodSpec.Builder setValue = null;
        if(genBuilder){        
			setValue = methodBuilder(PUBLIC(), void.class, "setValue" );
	        setValue.addAnnotation(Override.class);
	        addParameter(setValue,int.class, "ordinal");
	        addParameter(setValue,Object.class, "value");
	        setValue.addCode("super.setValue(ordinal, value);\n");
	        genConstrucotrsExt(def, cp, jackson);
        }else{
        	setValue = GenBuilder.genEnumSetter(def, cp,columnMetaBase);
        	GenBuilder.genConstructors(def, cp, jackson);
            GenImmutable.addEnumGetter(def, cp,columnMetaBase);
            if(jackson) GenImmutable.addDirectSerializer(def,cp);
        }
        setValue.addCode("this._changeSet |= (1L<<ordinal);\n");
        cp.addMethod(setValue.build());


        // *********************  IUpdateDelta
        
        addMethod(cp, PUBLIC(), boolean.class, "isEmpty", method -> {
        	method.addAnnotation(Override.class);
        	method.addCode("return _changeSet == 0;\n");
        });
        
        addMethod(cp, PUBLIC(), boolean.class, "isChanged", method -> {
        	method.addAnnotation(Override.class);
        	addParameter(method, columnMetaBase, "column");
        	method.addCode("return (_changeSet & (1L << column.ordinal())) != 0;\n");
        });
        
        addMethod(cp, PUBLIC(), boolean.class, "isChanged", method -> {
			method.addAnnotation(Override.class);
			addParameter(method, int.class, "ordinal");
			method.addCode("return (_changeSet & (1L << ordinal)) != 0;\n");
		});
        
        return cp;
	}
	
	
	public static void genConstrucotrsExt(EntityDef def, TypeSpec.Builder cp, boolean jackson){
        MethodSpec.Builder constr = constructorBuilder(PUBLIC());
        constr.addCode("super();\n");
        cp.addMethod(constr.build());

        constr = constructorBuilder(PUBLIC());
        addParameter(constr, def.type, "v");
        constr.addCode("super(v);");
        cp.addMethod(constr.build());

        constr = constructorBuilder(PUBLIC());

        constr.addCode("super(");
        
        int count = def.getProps().size();
        for(int i=0; i<count; i++) {
        	Property property = def.getProps().get(i);
        	addParameter(constr, property.type, property.name);        	

            constr.addCode(""+property.name+(i == count-1 ? "":","));
        }
        
        constr.addCode(");\n");
        cp.addMethod(constr.build());
	}
	
}
