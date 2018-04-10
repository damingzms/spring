package ${package};

/**
 * @company H&H Group
 * @author <a href="mailto:zhangmingsen@hh.global">Samuel Zhang</a>
 * @date 2018年4月2日 下午5:37:23
 */
public class Service {
	
	private String dtoPkg;
	
	private ServiceFactory factory;

	protected String getDtoPkg() {
		return dtoPkg;
	}

	protected void setDtoPkg(String dtoPkg) {
		this.dtoPkg = dtoPkg;
	}

	protected ServiceFactory getFactory() {
		return factory;
	}

	protected void setFactory(ServiceFactory factory) {
		this.factory = factory;
	}
	
}
