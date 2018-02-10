package ${package};

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Aspect
@Component
public class Transformer {
	
	/**
	 * 这里不使用ConcurrentHashMap。因为与其每次调用都需要运行一段同步代码块，不如初始的几次调用重复获取数据。这里的重复使用反射获取数据，不会产生问题
	 */
	private Map<String, List<String>> pathsCache = new HashMap<>();
	
	private Map<String, Class<?>> responseTypeCache = new HashMap<>();
	
	private String dtoPkg = "${dtoPackage}";
	
	@Autowired
	private ServiceFactory factory;

	@Around("execution(* ${servicePackage}.*.*(..))")
	public Object transform(ProceedingJoinPoint pjp) throws Exception {
		Object service = pjp.getTarget();
		Class<?> serviceClass = AopUtils.getTargetClass(service);
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		String serviceClassName = serviceClass.getName();
		String methodName = method.getName();
		String cacheKey = serviceClassName + "." + methodName;
		
		// path
		List<String> pathList = pathsCache.get(cacheKey);
		if (pathList == null) {
			String classPath = "";
			RequestMapping clsMappingAnnotation = serviceClass.getAnnotation(RequestMapping.class);
			if (clsMappingAnnotation != null) {
				String[] values = clsMappingAnnotation.value();
				if (values != null) {
					for (int i = 0; i < values.length; i++) {
						String value = values[i];
						if (!StringUtils.isEmpty(value)) {
							classPath = value;
							break;
						}
					}
				}
				if (StringUtils.isEmpty(classPath)) {
					String[] paths = clsMappingAnnotation.path();
					if (paths != null) {
						for (int i = 0; i < paths.length; i++) {
							String path = paths[i];
							if (!StringUtils.isEmpty(path)) {
								classPath = path;
								break;
							}
						}
					}
				}
			}

			String methodPath = "";
			RequestMapping methodMappingAnnotation = method.getAnnotation(RequestMapping.class);
			if (methodMappingAnnotation != null) {
				String[] values = methodMappingAnnotation.value();
				if (values != null) {
					for (int i = 0; i < values.length; i++) {
						String value = values[i];
						if (!StringUtils.isEmpty(value)) {
							methodPath = value;
							break;
						}
					}
				}
				if (StringUtils.isEmpty(methodPath)) {
					String[] paths = methodMappingAnnotation.path();
					if (paths != null) {
						for (int i = 0; i < paths.length; i++) {
							String path = paths[i];
							if (!StringUtils.isEmpty(path)) {
								methodPath = path;
								break;
							}
						}
					}
				}
			}
			String[] classPaths = classPath.split("/");
			String[] methodPaths = methodPath.split("/");
			pathList = new ArrayList<>();
			pathList.add(factory.getBasePath());
			pathList.addAll(Arrays.asList(classPaths));
			pathList.addAll(Arrays.asList(methodPaths));
			pathsCache.put(cacheKey, pathList);
		}
		UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
				.scheme(factory.getProtocol()).host(factory.getHost()).port(factory.getPort())
				.pathSegment(pathList.toArray(new String[] {}));
		
		// return type
		Class<?> responseType = responseTypeCache.get(cacheKey);
		if (responseType == null) {
			responseType = signature.getReturnType();
			responseTypeCache.put(cacheKey, responseType);
		}
		
		// request
		HttpEntity<?> httpEntity = null;
		Object[] args = pjp.getArgs();
		Parameter[] parameters = method.getParameters();
		if (args != null && args.length > 0) {
			for (int i = 0; i< args.length; i++) {
				Object arg = args[i];
				if (arg == null) {
					continue;
				}
				Parameter parameter = parameters[i];
				RequestBody bodyAnnotation = parameter.getAnnotation(RequestBody.class); // Spring MVC，一个方法只能有一个@RequestBody修饰的参数
				if (bodyAnnotation != null) {
					httpEntity = new HttpEntity<>(arg);
				} else if (parameter.getType().getPackage().getName().equals(dtoPkg)) {
					Map<String, Object> map = Utils.bean2Map(arg);
					if (!CollectionUtils.isEmpty(map)) {
						for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
							String key = it.next();
							Object value = map.get(key);
							if (value != null) {
								builder.queryParam(key, value);
							}
						}
					}
				} else {
					String name = null;
					RequestParam paramAnnotation = parameter.getAnnotation(RequestParam.class);
					if (paramAnnotation != null) {
						name = paramAnnotation.value();
						if (StringUtils.isEmpty(name)) {
							name = paramAnnotation.name();
						}
					}
					if (StringUtils.isEmpty(name)) {
						name = parameter.getName();
					}
					builder.queryParam(name, arg);
				}
			}
		}

		URI uri = builder.build().toUri();
		RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
		Object result = restTemplate.postForObject(uri, httpEntity, responseType);
		return result;
	}
	
	public static void main(String[] args) {
		System.out.println(new Date().toString());
	}
	
}
