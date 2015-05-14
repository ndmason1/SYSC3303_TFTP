package tftp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
	
	private static Properties prop;

	static {
		
		prop = new Properties();
		String propFileName = "config.properties";
 
		InputStream inputStream = Config.class.getClassLoader().getResourceAsStream(propFileName);
 
		if (inputStream != null) {
			try {
				prop.load(inputStream);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 
 		
	}
	
	public static boolean getDebug() {
		return Boolean.parseBoolean(prop.getProperty("debug"));
	}

}
