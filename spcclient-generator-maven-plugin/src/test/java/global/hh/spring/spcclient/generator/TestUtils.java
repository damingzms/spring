package global.hh.spring.spcclient.generator;

import java.util.Map;

import global.hh.spring.spcclient.generator.bean.Bean;

public class TestUtils {

	public static void main(String[] args) throws Exception {
		Map<String, Object> bean2Map = Utils.bean2Map(new Bean());
		System.out.println(bean2Map);
	}

}
