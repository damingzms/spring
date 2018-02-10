package ${package};

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class Utils {

	public static Map<String, Object> bean2Map(Object obj) throws Exception {
		Map<String, Object> map = new HashMap<>();
		BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		for (PropertyDescriptor descriptor : propertyDescriptors) {
			String name = descriptor.getName();
			if (!name.equals("class")) {
				Method getter = descriptor.getReadMethod();
				Object value = getter.invoke(obj);
				map.put(name, value);
			}
		}
		return map;
	}

}