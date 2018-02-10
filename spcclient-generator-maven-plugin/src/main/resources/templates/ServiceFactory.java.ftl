package ${package};

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
