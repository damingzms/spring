package wang.zhuping.spring.spcclient.generator;

import org.apache.commons.lang3.text.WordUtils;

public class TestStringUtils {

	public static void main(String[] args) {
		String factoryClassName = "asd-gds_EdggruCfdf";
		factoryClassName = WordUtils.capitalize(factoryClassName, '-', '_').replaceAll("[-, _]", "");
		System.out.println(factoryClassName);

	}

}
