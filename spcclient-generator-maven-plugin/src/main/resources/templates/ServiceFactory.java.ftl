package ${package};

/**
 * @company H&H Group
 * @author <a href="mailto:zhangmingsen@hh.global">Samuel Zhang</a>
 * @date 2018年2月11日 上午10:59:45
 */
public interface ServiceFactory {

	public String getProtocol();

	public void setProtocol(String protocol);

	public String getHost();

	public void setHost(String host);

	public int getPort();

	public void setPort(int port);

	public String getBasePath();

	public void setBasePath(String basePath);

}
