package edu.tiet.scms.api;

public final class ApiProblem extends RuntimeException {
  public final int status;
  public final String code;
  public ApiProblem(int status,String code,String message) { super(message);this.status=status;this.code=code; }
  public static void require(boolean condition,int status,String code,String message) {
    if(!condition) throw new ApiProblem(status,code,message);
  }
}
