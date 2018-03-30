package hr.hrg.hipster.processor;

import com.squareup.javapoet.ClassName;

public class HipsterProcessorUtil {

	public static final ClassName CN_JsonCreator  = ClassName.get("com.fasterxml.jackson.annotation","JsonCreator");
	public static final ClassName CN_JsonProperty = ClassName.get("com.fasterxml.jackson.annotation","JsonProperty");	
	public static final ClassName CN_JsonGenerator = ClassName.get("com.fasterxml.jackson.core","JsonGenerator");
	public static final ClassName CN_JsonIgnore = ClassName.get("com.fasterxml.jackson.annotation","JsonIgnore");
	public static final ClassName CN_JsonGenerationException = ClassName.get("com.fasterxml.jackson.core","JsonGenerationException");
	public static final ClassName CN_SerializerProvider = ClassName.get("com.fasterxml.jackson.databind","SerializerProvider");	
	public static final ClassName CN_JsonSerialize = ClassName.get("com.fasterxml.jackson.databind.annotation","JsonSerialize");

	public static boolean isType(Property p, String string, String string2) {
		return string.equals(p.type.toString()) || string2.equals(p.type.toString());
	}

	public static boolean isType(Property p, String string) {
		return string.equals(p.type.toString());
	}
	
	public static final String[] splitClassName(String className){
		int idx = className.lastIndexOf('.');
		String pkg = "";
		String shortName = className;
		if(idx != -1){
			pkg = className.substring(0,idx);
			shortName = className.substring(idx+1);
		}
		return new String[]{pkg,shortName};
	}
	
	public static final String justName(String className){
		return splitClassName(className)[1];
	}		
}
