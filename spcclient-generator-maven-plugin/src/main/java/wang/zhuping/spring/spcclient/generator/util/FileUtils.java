package wang.zhuping.spring.spcclient.generator.util;

import java.io.File;

public class FileUtils {
	
	/**
	 * @param parent 可为空
	 * @param childs
	 * @return
	 * @throws  NullPointerException
     *          If <code>childs</code> is <code>null</code>
	 */
	public static File mkdirs(File parent, String... childs) {
		for (int i = 0; i < childs.length; i++) {
			parent = new File(parent, childs[i]);
			parent.mkdirs();
		}
		return parent;
	}
	
	/**
	 * @param parent 可为空
	 * @param child
	 * @return
	 * @throws  NullPointerException
     *          If <code>child</code> is <code>null</code>
	 */
	public static File delete(File parent, String child) {
		File file = new File(parent, child);
		file.delete();
		return file;
	}

}
