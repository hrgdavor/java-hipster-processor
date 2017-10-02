package hr.hrg.hipster.processor;

public class AnnotationDef {

	public String className;
	public String params;

	public AnnotationDef(String className) {
		this.className = className;
	}
	
	public AnnotationDef(String className, String params) {
		this.className = className;
		this.params = params;
	}
	
}
