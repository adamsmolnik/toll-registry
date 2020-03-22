package cloud.developing.tollregistry;

import static software.amazon.awscdk.services.lambda.Function.fromFunctionArn;
import static software.amazon.awscdk.services.stepfunctions.Condition.booleanEquals;
import static software.amazon.awscdk.services.stepfunctions.StateMachineType.STANDARD;
import static software.amazon.awscdk.services.stepfunctions.Task.Builder.create;

import java.util.Map;

import org.jetbrains.annotations.NotNull;

import software.amazon.awscdk.core.CfnParameter;
import software.amazon.awscdk.core.CfnParameterProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.Pass;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.tasks.InvokeFunction;

class StepFunctionsStack extends Stack {

	private final String envType;

	StepFunctionsStack(Construct scope, String appName) {
		super(scope, appName, StackProps.builder().stackName(appName).build());
		this.envType = new CfnParameter(this, "envType", CfnParameterProps.builder().build()).getValueAsString();

		var ocr = create(this, "Perfrom OCR").task(new InvokeFunction(fromFunctionArn(this, "ocr", arnFromName("ocr"))))
		        .build();

		var metadataExtraction = create(this, "Extract Metadata").task(
		        new InvokeFunction(fromFunctionArn(this, "met	adata-extraction", arnFromName("metadata-extraction"))))
		        .build();

		var mergeAdapter = create(this, "Merge Results")
		        .task(new InvokeFunction(fromFunctionArn(this, "merge-adapter", arnFromName("merge-adapter")))).build();

		var ocrAndMetadataExtraction = new Parallel(this, "Perfrom OCR and Extract Metadata");
		ocrAndMetadataExtraction.branch(ocr);
		ocrAndMetadataExtraction.branch(metadataExtraction);
		ocrAndMetadataExtraction.branch(Pass.Builder.create(this, "Pass Input On").build());

		var db = create(this, "Save Results in DB")
		        .task(new InvokeFunction(fromFunctionArn(this, "db", arnFromName("db")))).build();

		var vehicleFinder = create(this, "Find Stolen Vehicle")
		        .parameters(Map.of("registration-number.$", "$.items[0].registration-number"))
		        .task(new InvokeFunction(fromFunctionArn(this, "vehicle-finder", arnFromName("vehicle-finder"))))
		        .build();

		var notifier = create(this, "Notify of Findings")
		        .parameters(Map.of("message.$", "$.message", "source", "toll-registry-" + envType))
		        .task(new InvokeFunction(fromFunctionArn(this, "notifier", arnFromName("notifier")))).build();

		var vehicleFound = new Choice(this, "Is the Stolen Vehicle Found?");
		vehicleFound.when(booleanEquals("$.vehicle-found", true), notifier);
		vehicleFound.otherwise(new Succeed(this, "Succeed"));

		var chain = ocrAndMetadataExtraction.next(mergeAdapter).next(db).next(vehicleFinder).next(vehicleFound);
		StateMachine.Builder.create(this, appName).stateMachineName("toll-registry-" + envType)
		        .stateMachineType(STANDARD).definition(chain).build();
	}

	private @NotNull String arnFromName(String functionName) {
		return "arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + functionName + "-" + envType;
	}

}
