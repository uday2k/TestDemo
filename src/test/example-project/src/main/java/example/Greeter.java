package example;

public class Greeter {

	private static final Greeter instance = new Greeter();

	public static Greeter instance() {
		return instance;
	}

	private Greeter() {}

	private String loginName = "World";
	private String response;

	public String getResponse() {
		return response;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public void respond(String query) {
		response = "Hello, " + loginName + "!";
	}

	public void leave() {
		response = "Goodbye, " + loginName + "!";
	}
}
