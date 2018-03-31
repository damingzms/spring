package global.hh.spring.spcclient.generator;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import global.hh.spring.spcclient.generator.bean.TestInterface;

public class TestJavapoet {
	
	public static void test1() {
		MethodSpec main = MethodSpec.methodBuilder("main")
			    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
			    .returns(void.class)
			    .addParameter(String[].class, "args")
			    .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
			    .build();
		MethodSpec test = MethodSpec.methodBuilder("test")
			    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
			    .returns(void.class)
			    .addParameter(String[].class, "args")
			    .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
			    .build();
		System.out.println(main.toString() + test.toString());
	}
	
	public static void test2() {
		ClassName className = ClassName.get(TestInterface.class);
		System.out.println(className);
		ClassName className1 = ClassName.get("cn.sam.test.springcloud.client.generator.bean", "TestInterface");
		System.out.println(className1);
	}
	
	public static void test3() {
		TypeSpec helloWorld = TypeSpec.enumBuilder("Roshambo")
			    .addModifiers(Modifier.PUBLIC)
			    .addEnumConstant("ROCK", TypeSpec.anonymousClassBuilder("$S", "fist")
			        .addMethod(MethodSpec.methodBuilder("toString")
			            .addAnnotation(Override.class)
			            .addModifiers(Modifier.PUBLIC)
			            .addStatement("return $S", "avalanche!")
			            .returns(String.class)
			            .build())
			        .build())
			    .addEnumConstant("SCISSORS", TypeSpec.anonymousClassBuilder("$S, $S", "peace", "ttt")
			        .build())
			    .addEnumConstant("PAPER", TypeSpec.anonymousClassBuilder("$S", "flat")
			        .build())
			    .addField(String.class, "handsign", Modifier.PRIVATE, Modifier.FINAL)
			    .addMethod(MethodSpec.constructorBuilder()
			        .addParameter(String.class, "handsign")
			        .addStatement("this.$N = $N", "handsign", "handsign")
			        .build())
			    .build();

		System.out.println(helloWorld.toString());
	}
	
	public static void test4() {
		TypeName typeName = TypeName.get(int.class);
		System.out.println(typeName);   // int
		
		ClassName className = ClassName.get(int.class);
		System.out.println(className);   // java.lang.IllegalArgumentException: primitive types cannot be represented as a ClassName
	}

	public static void main(String[] args) {
		test4();
	}

}
