package ${package};

import java.lang.reflect.Array;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @company H&H Group
 * @author <a href="mailto:zhangmingsen@hh.global">Samuel Zhang</a>
 * @date 2018年2月11日 上午10:59:45
 */
public class Transformer {
	
	private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);
	
	private static final RestTemplate REST_TEMPLATE;
	
	static {
		HttpClientBuilder b = HttpClientBuilder.create();
		
        RequestConfig requestConfig = RequestConfig.custom()
	            .setSocketTimeout(8000)
	            .setConnectTimeout(8000)
	            .setConnectionRequestTimeout(8000)
	            .build();
        b.setDefaultRequestConfig(requestConfig);

		// 信任所有
		SSLContext sslContext = null;
		try {
			sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
				public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
					return true;
				}
			}).build();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
		b.setSSLContext(sslContext);

		// 去掉主机验证
		HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;

		// 注册相关策略
		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", sslSocketFactory)
				.build();

		// 使用多线程
		PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		connMgr.setMaxTotal(200);
		connMgr.setDefaultMaxPerRoute(100);
		b.setConnectionManager(connMgr);

		// 生成httpclient
		CloseableHttpClient httpClient = b.build();
		
		REST_TEMPLATE = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

	public static <T> T transform(String dtoPkg, ServiceFactory factory, List<RequestMethod> requestMethodList, List<String> pathList,
			Object argNameWithRequestBody, List<Object> argNamesWithPathVariable, Map<String, Object> argNamesOther, Class<T> responseType)
			throws Exception {
		UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
				.scheme(factory.getProtocol()).host(factory.getHost()).port(factory.getPort())
				.path(factory.getBasePath())
				.pathSegment(pathList.toArray(new String[] {}))
				.path("/");
		
		HttpEntity<?> httpEntity = argNameWithRequestBody != null ? new HttpEntity<>(argNameWithRequestBody) : null;
		if (CollectionUtils.isEmpty(argNamesOther)) {
			for (Iterator<String> it = argNamesOther.keySet().iterator(); it.hasNext(); ) {
				String name = it.next();
				Object arg = argNamesOther.get(name);
				List<Object> argList = new ArrayList<>();
				Class<?> type = arg.getClass();
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
							for (Iterator<String> itSub = map.keySet().iterator(); itSub.hasNext(); ) {
								String key = itSub.next();
								Object value = map.get(key);
								if (value != null) {
									builder.queryParam(key, value);
								}
							}
						}
					} else {
						builder.queryParam(name, subArg);
					}
				}
			}
		}
		
		UriComponents uriComponents = builder.build();
		if (!CollectionUtils.isEmpty(argNamesWithPathVariable)) {
			uriComponents.expand(argNamesWithPathVariable.toArray());
		}
		URI uri = uriComponents.encode().toUri();
		
		LOG.info("调用接口，URI = {}，RequestBody = {}", uri.toString(), httpEntity != null ? Utils.toJson(httpEntity.getBody()) : null);
		StopWatch watch = new StopWatch();
		watch.start();
		T result = null;
		if (requestMethodList == null || requestMethodList.contains(RequestMethod.POST)) {
			result = REST_TEMPLATE.postForObject(uri, httpEntity, responseType);
		} else if (requestMethodList.contains(RequestMethod.GET)) {
			result = REST_TEMPLATE.getForObject(uri, responseType);
		} else if (requestMethodList.contains(RequestMethod.DELETE)) {
			REST_TEMPLATE.delete(uri);
		} else {
			throw new RuntimeException("Request method not support: " + requestMethodList);
		}
		watch.stop();
		LOG.info("接口返回，耗时 = {}，RequestBody = {}", watch.getTotalTimeMillis(), result != null ? Utils.toJson(result) : null);
		return result;
	}
	
}
