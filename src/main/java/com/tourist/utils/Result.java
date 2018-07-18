package com.tourist.utils;

public class Result {

	private Object resultValue;
	private String commandName;
	private String variableName;

	public Result(Object resultValue, String commandName, String variableName) {
		super();
		this.resultValue = resultValue;
		this.commandName = commandName;
		this.variableName = variableName;
	}

	public Object getResultValue() {
		return resultValue;
	}

	public void setResultValue(Object resultValue) {
		this.resultValue = resultValue;
	}

	public String getCommandName() {
		return commandName;
	}

	public void setCommandName(String commandName) {
		this.commandName = commandName;
	}

	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

}
