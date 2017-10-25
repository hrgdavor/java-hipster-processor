package hr.hrg.hipster.processor;


import static hr.hrg.javapoet.PoetUtil.*;

import com.squareup.javapoet.*;

public class GenDelta {

	public TypeSpec.Builder gen2(EntityDef def) {

		TypeSpec.Builder cp = classBuilder(PUBLIC(), def.typeDelta);
		
//		cp.superclass(parametrized(EnumGetterUpdateDelta.class, def.typeImmutable, def.typeEnum));
		
		MethodSpec.Builder constr = constructorBuilder(PUBLIC());
		addParameter(constr, long.class, "changeSet");
		addParameter(constr, def.typeImmutable, "obj");
		
		constr.addCode("super(changeSet, obj, $T.COLUMN_ARRAY);", def.typeMeta);
		cp.addMethod(constr.build());
		
		return cp;
	}	
	
}
