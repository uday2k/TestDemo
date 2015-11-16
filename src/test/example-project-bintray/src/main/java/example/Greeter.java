package example;

public class Greeter {

	private String loginName;

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String respond(String query) {
		if (loginName == null) {
			return "Hello, World!";
		} else {
			return "Hello, " + loginName + "!";
		}
	}

}
