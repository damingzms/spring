package global.hh.spring.spcclient.generator.util;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

/**
 * @company H&H Group
 * @author <a href="mailto:zhangmingsen@hh.global">Samuel Zhang</a>
 * @date 2018年2月11日 上午10:59:57
 */
public final class TemplateUtils {
	
	public static final String TEMPLATE_NAME_POSTFIX = ".ftl";
	
	public static final String TEMPLATE_NAME_POM = "pom.xml" + TEMPLATE_NAME_POSTFIX;
	
	public static final String TEMPLATE_NAME_SERVICE = "Service.java" + TEMPLATE_NAME_POSTFIX;
	
	public static final String TEMPLATE_NAME_SERVICE_FACTORY = "ServiceFactory.java" + TEMPLATE_NAME_POSTFIX;
	
	public static final String TEMPLATE_NAME_UTILS = "Utils.java" + TEMPLATE_NAME_POSTFIX;
	
	public static final String TEMPLATE_NAME_TRANSFORMER = "Transformer.java" + TEMPLATE_NAME_POSTFIX;
	
	private static final String TEMPLATE_DIRECTORY = "/templates";
	
	private static final Configuration FREEMARKER_CFG = new Configuration(Configuration.VERSION_2_3_27);

	static {
		try {
			FREEMARKER_CFG.setClassLoaderForTemplateLoading(TemplateUtils.class.getClassLoader(), TEMPLATE_DIRECTORY);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		FREEMARKER_CFG.setDefaultEncoding("UTF-8");
		FREEMARKER_CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
	}
	
	public static Configuration getFreemarkerConfiguration() {
		return FREEMARKER_CFG;
	}
	
	public static String templateName2FileName(String templateName) {
		int endIndex = templateName.lastIndexOf(TEMPLATE_NAME_POSTFIX);
		return templateName.substring(0, endIndex);
	}
	
}
