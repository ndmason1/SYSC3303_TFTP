package tftp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.naming.ConfigurationException;

import tftp.Logger.LogLevel;

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
	
	public static LogLevel getLogLevel() throws ConfigurationException {
		String levelStr = prop.getProperty("log_level").toUpperCase();
		for (LogLevel level : LogLevel.values()) {
			if (level.name().equals(levelStr))
				return level;
		}
		
		throw new ConfigurationException("bad config value specified for log_level");
	}

}
