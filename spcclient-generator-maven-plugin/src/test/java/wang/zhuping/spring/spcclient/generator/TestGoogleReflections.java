package wang.zhuping.spring.spcclient.generator;

import java.lang.reflect.Field;
import java.util.Set;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;

import com.google.common.base.Predicate;

public class TestGoogleReflections {

	public static void main(String[] args) throws Exception {
		Reflections reflections = new Reflections("cn.sam.test.springcloud.client.generator");
		Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Mojo.class);
		Class<?> gmClass = classes.iterator().next();
		System.out.println(gmClass.getName());
		
		Predicate<Field> predicate = ReflectionUtils.withAnnotation(Parameter.class);
		@SuppressWarnings("unchecked")
		Set<Field> allFields = ReflectionUtils.getFields(gmClass, predicate); // Parameter注解Retention=Class，所以这里返回属性集合为空
		System.out.println(allFields.size());
	}

}
