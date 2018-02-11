package ${package};

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Aspect
@Component
public class Transformer {
	
	private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);
	
	/**
	 * 这里不使用ConcurrentHashMap。因为与其每次调用都需要运行一段同步代码块，不如初始的几次调用重复获取数据。这里的重复使用反射获取数据，不会产生问题
	 */
	private Map<String, List<String>> pathsCache = new HashMap<>();
	
	private Map<String, List<RequestMethod>> requestMethodCache = new HashMap<>();
	
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
		
		// path & method
		List<String> pathList = pathsCache.get(cacheKey);
		List<RequestMethod> requestMethodList = requestMethodCache.get(cacheKey);
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
				
				RequestMethod[] methods = clsMappingAnnotation.method();
				if (methods != null && methods.length > 0) {
					requestMethodList = Arrays.asList(methods);
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
				
				if (CollectionUtils.isEmpty(requestMethodList)) {
					RequestMethod[] methods = methodMappingAnnotation.method();
					if (methods != null && methods.length > 0) {
						requestMethodList = Arrays.asList(methods);
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
		List<Object> uriVariables = new ArrayList<>();
		HttpEntity<?> httpEntity = null;
		Object[] args = pjp.getArgs();
		Parameter[] parameters = method.getParameters();
		if (args != null && args.length > 0) {
			for (int i = 0; i< args.length; i++) {
				Object arg = args[i];
				Parameter parameter = parameters[i];
				PathVariable pathVariableAnnotation = parameter.getAnnotation(PathVariable.class);
				if (pathVariableAnnotation != null) {
					uriVariables.add(arg);
					continue;
				}
				if (arg == null) {
					continue;
				}
				RequestBody bodyAnnotation = parameter.getAnnotation(RequestBody.class); // Spring MVC，一个方法只能有一个@RequestBody修饰的参数
				if (bodyAnnotation != null) {
					httpEntity = new HttpEntity<>(arg);
				} else {
					List<Object> argList = new ArrayList<>();
					Class<?> type = parameter.getType();
					if (type.isArray()) {
						for (int j = 0; j < Array.getLength(arg); j++) {
							argList.add(Array.get(arg, j));
						}
					} else if (type.isAssignableFrom(Collection.class)) {
						@SuppressWarnings("unchecked")
						Collection<Object> collection = (Collection<Object>) arg;
						argList = new ArrayList<>(collection);
					} else {
						argList.add(arg);
					}
					for (Object subArg : argList) {
						Class<?> subArgClass = subArg.getClass();
						if (subArgClass.getPackage() != null && subArgClass.getPackage().getName().equals(dtoPkg)) {
							Map<String, Object> map = Utils.bean2Map(subArg);
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
							builder.queryParam(name, subArg);
						}
					}
				}
			}
		}
		
		URI uri = builder.buildAndExpand(uriVariables.toArray()).encode().toUri();
		RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
		LOG.info("调用接口，URI = {}，RequestBody = {}", uri.toString(), httpEntity != null ? Utils.toJson(httpEntity.getBody()) : null);
		StopWatch watch = new StopWatch();
		watch.start();
		Object result = null;
		if (requestMethodList == null || requestMethodList.contains(RequestMethod.POST)) {
			result = restTemplate.postForObject(uri, httpEntity, responseType);
		} else if (requestMethodList.contains(RequestMethod.GET)) {
			result = restTemplate.getForObject(uri, responseType);
		} else if (requestMethodList.contains(RequestMethod.DELETE)) {
			restTemplate.delete(uri);
		} else {
			throw new RuntimeException("Request method not support: " + requestMethodList);
		}
		watch.stop();
		LOG.info("接口返回，耗时 = {}，RequestBody = {}", watch.getTotalTimeMillis(), result != null ? Utils.toJson(result) : null);
		return result;
	}
	
	public static void main(String[] args) {
		System.out.println(new Date().toString());
	}
	
}

