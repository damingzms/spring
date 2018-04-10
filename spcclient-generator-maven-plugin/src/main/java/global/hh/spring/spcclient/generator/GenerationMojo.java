package global.hh.spring.spcclient.generator;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Predicate;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import freemarker.template.Template;
import global.hh.spring.spcclient.generator.util.FileUtils;
import global.hh.spring.spcclient.generator.util.TemplateUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 插件会从项目代码编译输出目录搜索相关的类，所以需要在项目编译完成之后运行
 * @company H&H Group
 * @author <a href="mailto:zhangmingsen@hh.global">Samuel Zhang</a>
 * @date 2018年2月11日 上午11:00:20
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PACKAGE)
public class GenerationMojo extends AbstractMojo {

//	@Parameter(defaultValue = "${project}", readonly = true)
//	private MavenProject project;
	
	@Parameter(defaultValue = "${plugin}", readonly = true)
	private PluginDescriptor pluginDescriptor;

	/**
	 * 当前项目编译输出目录，不允许用户配置
	 */
	@Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
	private String currentProjectOutputDirectory;

	/**
	 * 当前项目根目录，不允许用户配置
	 */
	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private String currentProjectBaseDir;
	
	/**
	 * 扫描此包及其子包下面的Controller和DTO。多个包以“,”分开
	 */
	@Parameter(defaultValue = "${project.groupId}", required = true)
	private String packagesToScan;
	
	/**
	 * 客户端项目groupId
     */
	@Parameter(defaultValue = "${project.groupId}", required = true)
	private String groupId;
	
	/**
	 * 客户端项目artifactId
     */
	@Parameter(defaultValue = "${project.artifactId}-client", required = true)
	private String artifactId;

	/**
	 * 客户端项目version
     */
	@Parameter(defaultValue = "${project.version}", required = true)
	private String version;

	/**
	 * 生成的项目根目录。默认值：与当前项目根目录同级的名为artifactId参数值的目录
	 */
	@Parameter
	private String baseDir;
	
	/**
	 * src目录，不允许用户配置
     */
	@Parameter(defaultValue = "/src/main/java", required = true, readonly = true)
	private String srcDir;
	
	/**
	 * 基础包名。Value中的“-”和“_”符号会替换成“.”
     */
	@Parameter(defaultValue = "${project.groupId}.${project.artifactId}.client", required = true)
	private String basePkg;
	
	/**
	 * 生成DTO类目标包。默认值：{basePkg}.dto
     */
	@Parameter
	private String dtoPkg;
	
	/**
	 * 生成service类目标包。默认值：{basePkg}.service
     */
	@Parameter
	private String servicePkg;
	
	/**
	 * Value中的“-”和“_”符号会删除，单词首字母自动大写
     */
	@Parameter(defaultValue = "${project.artifactId}ServiceFactory", required = true)
	private String factoryClassName;
	
	private File basePkgFile;
	
	private File baseDirFile;
	
	private File srcDirFile;
	
	public void execute() throws MojoExecutionException {
		Log log = getLog();
		log.info("Generating Spring Cloud client project.");
		
		// PREPARE
		basePkg = basePkg.replaceAll("[-, _]", ".");
		dtoPkg = basePkg + ".dto";
		servicePkg = basePkg + ".service";
		factoryClassName = WordUtils.capitalize(factoryClassName, '-', '_').replaceAll("[-, _]", "");
		
		try {
			
			// SCAN class
			ClassRealm realm = pluginDescriptor.getClassRealm();
			File elementFile = new File(currentProjectOutputDirectory); // maven插件运行时加载项目的类，但是不加载项目依赖的类
			realm.addURL(elementFile.toURI().toURL());
			
			Set<Class<?>> controllerClasses = new HashSet<>();
			Reflections reflections = new Reflections(packagesToScan);
			controllerClasses.addAll(reflections.getTypesAnnotatedWith(Controller.class));
			controllerClasses.addAll(reflections.getTypesAnnotatedWith(RestController.class));
			if (CollectionUtils.isEmpty(controllerClasses)) {
				log.warn("Cannot find any Controller in package or sub-package: " + packagesToScan);
				return;
			}
			
			// GENERATE Project Structure
			genProjectStructure();
			
			// JAVAFILES
			Map<Class<?>, JavaFile> dtoJavaFileMap = new HashMap<>();
			Map<Class<?>, ClassName> dtoClassNameMap = new HashMap<>();
			List<JavaFile> serviceJavaFiles = new ArrayList<>();
			List<ClassName> serviceClassNames = new ArrayList<>();
			Map<String, Integer> dtoNameCountMap = new HashMap<>();
			Map<String, Integer> serviceNameCountMap = new HashMap<>();
			for (Class<?> controllerClasse : controllerClasses) {
				
				// SERVICE
				// service class
				String serviceName = controllerClasse.getSimpleName();
				if (serviceName.endsWith("Controller")) {
					serviceName = serviceName.substring(0, serviceName.lastIndexOf("Controller"));
				} 
				if (!serviceName.endsWith("Service")) {
					serviceName = serviceName + "Service";
				}
				Integer serviceNameCount = serviceNameCountMap.get(serviceName);
				if (serviceNameCount == null || serviceNameCount == 0) {
					serviceNameCount = 1;
					serviceNameCountMap.put(serviceName, serviceNameCount);
				} else {
					serviceNameCount += 1;
					serviceNameCountMap.put(serviceName, serviceNameCount);
					serviceName += serviceNameCount;
				}
				ClassName className = ClassName.get(servicePkg, serviceName);
				serviceClassNames.add(className);
				Builder serviceTypeSpecBuilder = TypeSpec.classBuilder(serviceName)
					    .addModifiers(Modifier.PUBLIC)
					    .superclass(ClassName.get(basePkg, "Service"));
				
				// service class annotation
				List<RequestMethod> requestMethodList = null;
				String classMapping = "";
				RequestMapping classRequestMappingAnno = controllerClasse.getAnnotation(RequestMapping.class);
				if (classRequestMappingAnno != null) {
					String[] values = classRequestMappingAnno.value();
					if (values != null) {
						for (int i = 0; i < values.length; i++) {
							String value = values[i];
							if (!StringUtils.isEmpty(value)) {
								classMapping = value;
								break;
							}
						}
					}
					if (StringUtils.isEmpty(classMapping)) {
						String[] paths = classRequestMappingAnno.path();
						if (paths != null) {
							for (int i = 0; i < paths.length; i++) {
								String path = paths[i];
								if (!StringUtils.isEmpty(path)) {
									classMapping = path;
									break;
								}
							}
						}
					}
					
					RequestMethod[] requestMethods = classRequestMappingAnno.method();
					if (requestMethods != null && requestMethods.length > 0) {
						requestMethodList = Arrays.asList(requestMethods);
					}
				
				}
				
				// service constructor
				MethodSpec constructor = MethodSpec.constructorBuilder()
						.addModifiers(Modifier.PUBLIC)
						.addParameter(String.class, "dtoPkg")
						.addParameter(ClassName.get(basePkg, "ServiceFactory"), "factory")
						.addStatement("this.setDtoPkg(dtoPkg)")
						.addStatement("this.setFactory(factory)")
						.build();
				serviceTypeSpecBuilder.addMethod(constructor);
				
				// service method
				Predicate<Method> methodPredicate = ReflectionUtils.withAnnotation(RequestMapping.class);
				@SuppressWarnings("unchecked")
				Set<Method> methods = ReflectionUtils.getMethods(controllerClasse, methodPredicate);
				if (CollectionUtils.isEmpty(methods)) {
					log.warn("Cannot find any mapping method in class: " + controllerClasse.getName());
				} else {
					for (Method method : methods) {
						
						// RESPONSE DTO
						Type returnType = method.getGenericReturnType();
						TypeName returnClassName = genDtoJavaFile(dtoJavaFileMap, dtoClassNameMap, dtoNameCountMap, returnType);;
						
						// service method
						com.squareup.javapoet.MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(method.getName())
								.addModifiers(Modifier.PUBLIC)
								.returns(returnClassName)
								.addException(Exception.class);
						
						// service method annotation
						String methodMapping = "";
						RequestMapping methodRequestMappingAnno = method.getAnnotation(RequestMapping.class);
						String[] values = methodRequestMappingAnno.value();
						if (values != null) {
							for (int i = 0; i < values.length; i++) {
								String value = values[i];
								if (!StringUtils.isEmpty(value)) {
									methodMapping = value;
									break;
								}
							}
						}
						if (StringUtils.isEmpty(methodMapping)) {
							String[] paths = methodRequestMappingAnno.path();
							if (paths != null) {
								for (int i = 0; i < paths.length; i++) {
									String path = paths[i];
									if (!StringUtils.isEmpty(path)) {
										methodMapping = path;
										break;
									}
								}
							}
						}
						if (CollectionUtils.isEmpty(requestMethodList)) {
							RequestMethod[] requestMethods = methodRequestMappingAnno.method();
							if (requestMethods != null && requestMethods.length > 0) {
								requestMethodList = Arrays.asList(requestMethods);
							}
						}

						// service method parameter
						String argNameWithRequestBody = null;
						List<String> argNamesWithPathVariable = new ArrayList<>();
						List<String> argNamesOther = new ArrayList<>();
						java.lang.reflect.Parameter[] parameters = method.getParameters();
						if (parameters != null && parameters.length > 0) {
							List<ParameterSpec> parameterSpecList = new ArrayList<>();
							for (int i = 0; i < parameters.length; i++) {
								java.lang.reflect.Parameter parameter = parameters[i];
								
								// REQUEST DTO
								Type parameterType = parameter.getParameterizedType();
								TypeName parameterClassName = genDtoJavaFile(dtoJavaFileMap, dtoClassNameMap, dtoNameCountMap, parameterType);
								
								// service method parameter
								String name = null;
								RequestParam requestParamAnno = parameter.getAnnotation(RequestParam.class);
								if (requestParamAnno != null) {
									name = requestParamAnno.value();
									if (StringUtils.isEmpty(name)) {
										name = requestParamAnno.name();
									}
								}
								if (i == 0) {
									name = "request";
								}
								if (StringUtils.isEmpty(name)) {
									name = parameter.getName();
								}
								com.squareup.javapoet.ParameterSpec.Builder parameterSpecBuilder = ParameterSpec.builder(parameterClassName, name, new Modifier[]{});
								
								// service method parameter annotation
								RequestBody requestBodyAnno = parameter.getAnnotation(RequestBody.class);
								PathVariable pathVariableAnno = parameter.getAnnotation(PathVariable.class);
								if (requestBodyAnno != null) {
									argNameWithRequestBody = name;
								} else if (pathVariableAnno != null) {
									argNamesWithPathVariable.add(name);
								} else {
									argNamesOther.add(name);
								}
								
								parameterSpecList.add(parameterSpecBuilder.build());
							}
							methodSpecBuilder.addParameters(parameterSpecList);
						}

						// service method code
						methodSpecBuilder.addStatement("$T<$T> requestMethodList = new $T<>()", List.class, RequestMethod.class, ArrayList.class);
						if (requestMethodList != null) {
							for (RequestMethod rm : requestMethodList) {
								methodSpecBuilder.addStatement("requestMethodList.add($T.$L)", RequestMethod.class, rm.name());
							}
						}
						methodSpecBuilder.addStatement("$T<$T> pathList = new $T<>()", List.class, String.class, ArrayList.class);
						String[] classPaths = classMapping.split("/");
						String[] methodPaths = methodMapping.split("/");
						for (String cp : classPaths) {
							if (StringUtils.isNotBlank(cp)) {
								methodSpecBuilder.addStatement("pathList.add($S)", cp);
							}
						}
						for (String mp : methodPaths) {
							if (StringUtils.isNotBlank(mp)) {
								methodSpecBuilder.addStatement("pathList.add($S)", mp);
							}
						}
						methodSpecBuilder.addStatement("$T<$T> argNamesWithPathVariable = new $T<>()", List.class, Object.class, ArrayList.class);
						if (argNamesWithPathVariable != null) {
							for (String an : argNamesWithPathVariable) {
								methodSpecBuilder.addStatement("argNamesWithPathVariable.add($L)", an);
							}
						}
						methodSpecBuilder.addStatement("$T<$T, $T> argNamesOther = new $T<>()", Map.class, String.class, Object.class, HashMap.class);
						if (argNamesOther != null) {
							for (String an : argNamesOther) {
								methodSpecBuilder.addStatement("argNamesOther.put($S, $L)", an, an);
							}
						}
						ClassName transformerClassName = ClassName.get(basePkg, "Transformer");
						Class<?> returnClass = method.getReturnType();
						String lastStatement = "";
						if (returnClass != void.class) {
							lastStatement = "return ";
						}
						methodSpecBuilder.addStatement(
								lastStatement
										+ "$T.transform(getDtoPkg(), getFactory(), requestMethodList, pathList, $L, argNamesWithPathVariable, argNamesOther, $T.class)",
								transformerClassName, argNameWithRequestBody, dtoClassNameMap.get(returnClass));
						
						serviceTypeSpecBuilder.addMethod(methodSpecBuilder.build());
					}
				}
				
				TypeSpec serviceTypeSpec = serviceTypeSpecBuilder.build();
				JavaFile serviceJavaFile = JavaFile.builder(servicePkg, serviceTypeSpec).build();
				serviceJavaFiles.add(serviceJavaFile);
			}
			// GENERATE DTOs
			outputJavaFiles(srcDirFile, dtoJavaFileMap.values());
			
			// GENERATE Services
			genService();
			outputJavaFiles(srcDirFile, serviceJavaFiles);
			
			// GENERATE Factory
			genFactory(serviceClassNames);
			
			// GENERATE Util
			genUtil();
			
			// GENERATE Transformer
			genTransformer();
			
			// GENERATE POM
			genPom();

			log.info("Successfully Generated Spring Cloud client project: " + baseDirFile.getAbsolutePath());
		} catch (Throwable e) {
			log.error(e);
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}
	
	private void genProjectStructure() throws Exception {
		if (baseDir == null) {
			baseDirFile = new File(currentProjectBaseDir, ".." + File.separator + artifactId);
			baseDir = baseDirFile.getPath();
		} else {
			baseDirFile = new File(baseDir);
		}
		srcDirFile = FileUtils.mkdirs(baseDirFile, srcDir);
		FileUtils.mkdirs(srcDirFile, dtoPkg.replace('.', File.separatorChar));
		FileUtils.mkdirs(srcDirFile, servicePkg.replace('.', File.separatorChar));
		basePkgFile = new File(srcDirFile, basePkg.replace('.', File.separatorChar));
	}
	
	private TypeName genDtoJavaFile(Map<Class<?>, JavaFile> javaFileMap, Map<Class<?>, ClassName> classNameMap, Map<String, Integer> nameCountMap, Type type) throws Exception {
		Class<?> clazz = null;
		if (type instanceof Class<?>) {
			clazz = (Class<?>) type;
			if (clazz.isArray()) {
				Class<?> componentType = clazz.getComponentType();
				TypeName typeName = genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, componentType);
				return ArrayTypeName.of(typeName);
			}
		} else if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			Type rawType = parameterizedType.getRawType();
			Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
			ClassName rawTypeName = (ClassName) genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, rawType);
			List<TypeName> argumentTypeNameList = new ArrayList<>();
			if (actualTypeArguments != null) {
				for (Type actualTypeArgument : actualTypeArguments) {
					TypeName typeName = genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, actualTypeArgument);
					argumentTypeNameList.add(typeName);
				}
			}
			return ParameterizedTypeName.get(rawTypeName, argumentTypeNameList.toArray(new TypeName[]{}));
		} else if (type instanceof WildcardType) {
			WildcardType wildcardType = (WildcardType) type;
			Type[] lowerBounds = wildcardType.getLowerBounds();
			Type[] upperBounds = wildcardType.getUpperBounds();
			if (lowerBounds != null) {
				for (Type lowerBound : lowerBounds) {
					genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, lowerBound);
				}
			}
			if (upperBounds != null) {
				for (Type upperBound : upperBounds) {
					genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, upperBound);
				}
			}
			return WildcardTypeName.get(wildcardType);

		} else if (type instanceof TypeVariable<?>) {
			TypeVariable<?> typeVariable = (TypeVariable<?>) type;
			return TypeVariableName.get(typeVariable);

		} else if (type instanceof GenericArrayType) {
			GenericArrayType genericArrayType = (GenericArrayType) type;
			Type genericComponentType = genericArrayType.getGenericComponentType();
			genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, genericComponentType);
			return ArrayTypeName.get(genericArrayType);

		} else {
			throw new IllegalArgumentException("unexpected type: " + type);
		}
		if (classNameMap.containsKey(clazz)) {
			return classNameMap.get(clazz);
		}
		
		String fullName = clazz.getName();
		if (!fullName.startsWith(packagesToScan) || fullName.startsWith("java") || fullName.startsWith("javax")
				|| fullName.startsWith("org.springframework")) {
			return TypeName.get(clazz);
		}
		
		// name
		String dtoName = clazz.getSimpleName();
		Integer dtoNameCount = nameCountMap.get(dtoName);
		if (dtoNameCount == null || dtoNameCount == 0) {
			dtoNameCount = 1;
			nameCountMap.put(dtoName, dtoNameCount);
		} else {
			dtoNameCount += 1;
			nameCountMap.put(dtoName, dtoNameCount);
			dtoName += dtoNameCount;
		}
		
		// type parameter
		List<TypeVariableName> typeVariables = new ArrayList<>();
		TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
		if (typeParameters != null) {
			for (TypeVariable<?> typeVariable : typeParameters) {
				typeVariables.add(TypeVariableName.get(typeVariable));
			}
		}
		
		// type
		ClassName className = ClassName.get(dtoPkg, dtoName);
		classNameMap.put(clazz, className); // 此语句需要放到递归调用之前，否则互相嵌套的DTO类会出现死循环
		Builder dtoTypeSpecBuilder = null;
		if (clazz.isInterface()) {
			dtoTypeSpecBuilder = TypeSpec.interfaceBuilder(dtoName);
		} else if (clazz.isEnum()) {
			dtoTypeSpecBuilder = TypeSpec.enumBuilder(dtoName).addAnnotation(Getter.class).addAnnotation(AllArgsConstructor.class);
		} else {
			dtoTypeSpecBuilder = TypeSpec.classBuilder(dtoName).addAnnotation(Getter.class).addAnnotation(Setter.class);
		}
		dtoTypeSpecBuilder.addTypeVariables(typeVariables).addModifiers(Modifier.PUBLIC);
		
		// super type
		Class<?>[] interfaces = clazz.getInterfaces();
		if (interfaces != null && interfaces.length > 0) {
			for (Class<?> superInterface : interfaces) {
				TypeName superClassName = genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, superInterface);
				dtoTypeSpecBuilder.addSuperinterface(superClassName);
			}
		}
		if (!clazz.isEnum()) {  // only classes have super classes, not ENUM
			Class<?> superclass = clazz.getSuperclass();
			if (superclass != null) {
				TypeName superClassName = genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, superclass);
				dtoTypeSpecBuilder.superclass(superClassName);
			}
		}
		
		// field
		Field[] fields = clazz.getDeclaredFields();
		if (fields != null && fields.length > 0) {
			List<FieldSpec> fieldSpecList = new ArrayList<>();
			for (Field field : fields) {
				
				// modifier
				List<Modifier> modifierList = new ArrayList<>();
				int modifiers = field.getModifiers();
				if (java.lang.reflect.Modifier.isPublic(modifiers)) modifierList.add(Modifier.PUBLIC);
				if (java.lang.reflect.Modifier.isPrivate(modifiers)) modifierList.add(Modifier.PRIVATE);
				if (java.lang.reflect.Modifier.isProtected(modifiers)) modifierList.add(Modifier.PROTECTED);
				if (java.lang.reflect.Modifier.isStatic(modifiers)) {
					modifierList.add(Modifier.STATIC);
					
					// 排除掉Enum的Constant和一些原生的属性
					if (clazz.isEnum()) {
						continue;
					}
				}
				if (java.lang.reflect.Modifier.isFinal(modifiers)) modifierList.add(Modifier.FINAL);
				
				// field type
				Type fieldType = field.getGenericType();
				TypeName fieldClassName = genDtoJavaFile(javaFileMap, classNameMap, nameCountMap, fieldType);
				
				// name
				String name = field.getName();
				
				com.squareup.javapoet.FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(fieldClassName, name, modifierList.toArray(new Modifier[]{}));
				
				// value，只支持静态成员属性，只支持原始类型、包装类和、String、Enum
				if (modifierList.contains(Modifier.STATIC)) {
					Class<?> fieldClass = field.getType();
					Object value = field.get(null);
					if (value != null) {
						if (fieldClass == String.class) {
							fieldSpecBuilder.initializer("$S", value);
						} else if (ClassUtils.isPrimitiveOrWrapper(fieldClass)) {
							fieldSpecBuilder.initializer("new $T($S)", ClassUtils.resolvePrimitiveIfNecessary(fieldClass), value.toString());
						} else if (fieldClass.getSuperclass() == Enum.class) {
							fieldSpecBuilder.initializer("$T.$L", fieldClassName, ((Enum<?>) value).name());
						}
					}
				}
				
				fieldSpecList.add(fieldSpecBuilder.build());
			}
			dtoTypeSpecBuilder.addFields(fieldSpecList);
		}
		
		if (clazz.isEnum()) {
			Enum<?>[] enumConstants = (Enum<?>[]) clazz.getEnumConstants();
			if (enumConstants != null) {
				for (Enum<?> enumConstant : enumConstants) {
					List<String> formats = new ArrayList<>();
					List<Object> args = new ArrayList<>();
					if (fields != null && fields.length > 0) {
						for (Field field : fields) {
							int modifiers = field.getModifiers();
							if (java.lang.reflect.Modifier.isStatic(modifiers)) {
								continue;
							}
							field.setAccessible(true);
							Object value = field.get(enumConstant);
							if (value != null) {
								Class<?> fieldClass = field.getType();
								if (fieldClass == String.class) {
									formats.add("$S");
									args.add(value);
								} else if (ClassUtils.isPrimitiveOrWrapper(fieldClass)) {
									formats.add("new $T($S)");
									args.add(ClassUtils.resolvePrimitiveIfNecessary(fieldClass));
									args.add(value.toString());
								} else if (fieldClass.getSuperclass() == Enum.class) {
									formats.add("$T.$L");
									args.add(classNameMap.get(fieldClass));
									args.add(((Enum<?>) value).name());
								}
							} else {
								formats.add("null");
							}
						}
					}
					dtoTypeSpecBuilder.addEnumConstant(enumConstant.name(), TypeSpec.anonymousClassBuilder(StringUtils.join(formats, ", "), args.toArray()).build());
				}
			}
		}
		
		TypeSpec typeSpec = dtoTypeSpecBuilder.build();
		JavaFile javaFile = JavaFile.builder(dtoPkg, typeSpec).build();
		javaFileMap.put(clazz, javaFile);
		return className;
	}
	
	private void genFactory(List<ClassName> serviceClassNames) throws Exception {
		
		// factory interface
		Map<String, Object> root = new HashMap<>();
		root.put("package", basePkg);
		outputFileFromTemplate(basePkgFile, TemplateUtils.TEMPLATE_NAME_SERVICE_FACTORY, root);
		
		// factory impl
		ClassName interfaceClassName = ClassName.get(basePkg, "ServiceFactory");
		Builder builder = TypeSpec.classBuilder(factoryClassName)
				.addAnnotation(Getter.class)
				.addAnnotation(Setter.class)
				.addModifiers(Modifier.PUBLIC)
				.addSuperinterface(interfaceClassName);
		com.squareup.javapoet.FieldSpec.Builder protocolBuilder = FieldSpec.builder(String.class, "protocol", Modifier.PRIVATE).initializer("$S", "http");
		builder.addField(protocolBuilder.build());
		com.squareup.javapoet.FieldSpec.Builder hostBuilder = FieldSpec.builder(String.class, "host", Modifier.PRIVATE).initializer("$S", "127.0.0.1");
		builder.addField(hostBuilder.build());
		com.squareup.javapoet.FieldSpec.Builder portBuilder = FieldSpec.builder(int.class, "port", Modifier.PRIVATE).initializer("$L", -1);
		builder.addField(portBuilder.build());
		com.squareup.javapoet.FieldSpec.Builder basePathBuilder = FieldSpec.builder(String.class, "basePath", Modifier.PRIVATE).initializer("$S", "");
		builder.addField(basePathBuilder.build());

		for (ClassName className : serviceClassNames) {
			com.squareup.javapoet.FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(className, StringUtils.uncapitalize(className.simpleName()), Modifier.PRIVATE)
					.initializer("new $T($S, this)", className, dtoPkg);
			builder.addField(fieldSpecBuilder.build());
		}
		TypeSpec typeSpec = builder.build();
		JavaFile javaFile = JavaFile.builder(basePkg, typeSpec).build();
		outputJavaFile(srcDirFile, javaFile);
	}
	
	private void genService() throws Exception {
		Map<String, Object> root = new HashMap<>();
		root.put("package", basePkg);
		outputFileFromTemplate(basePkgFile, TemplateUtils.TEMPLATE_NAME_SERVICE, root);
	}
	
	private void genUtil() throws Exception {
		Map<String, Object> root = new HashMap<>();
		root.put("package", basePkg);
		outputFileFromTemplate(basePkgFile, TemplateUtils.TEMPLATE_NAME_UTILS, root);
	}
	
	private void genTransformer() throws Exception {
		Map<String, Object> root = new HashMap<>();
		root.put("package", basePkg);
		outputFileFromTemplate(basePkgFile, TemplateUtils.TEMPLATE_NAME_TRANSFORMER, root);
	}
	
	private void genPom() throws Exception {
		Map<String, Object> root = new HashMap<>();
		root.put("groupId", groupId);
		root.put("artifactId", artifactId);
		root.put("version", version);
		outputFileFromTemplate(baseDirFile, TemplateUtils.TEMPLATE_NAME_POM, root);
	}

	private void outputFileFromTemplate(File dir, String templateName, Object dataModel) throws Exception {
		Template template = TemplateUtils.getFreemarkerConfiguration().getTemplate(templateName);
		File file = new File(dir, TemplateUtils.templateName2FileName(templateName));
	    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
			template.process(dataModel, writer);
	    }
	}
	
	private void outputJavaFile(File dir, JavaFile javaFile) throws Exception {
		javaFile.writeTo(dir);
	}
	
	private void outputJavaFiles(File dir, Collection<JavaFile> javaFiles) throws Exception {
		for (JavaFile javaFile : javaFiles) {
			outputJavaFile(dir, javaFile);
		}
	}
	
}