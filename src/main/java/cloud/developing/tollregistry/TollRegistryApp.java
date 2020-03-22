package cloud.developing.tollregistry;

import software.amazon.awscdk.core.App;

public class TollRegistryApp {

	public static void main(String[] args) throws Exception {
		var app = new App();
		var appName = "toll-registry";
		new StepFunctionsStack(app, appName);
		app.synth();
	}

}
