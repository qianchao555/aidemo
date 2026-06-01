package com.xiaofuzi.ai.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 3685616787397099481L;

    private boolean status;

    private int code;

    private String msg;

    private T data;

    public static <T> Result<T> success(T resultObject) {
        Result<T> Result = new Result<T>();
        Result.setStatus(true);
        Result.setMsg("success");
        Result.setCode(200);
        Result.setData(resultObject);
        return Result;
    }


    public static <T> Result<T> success() {
        Result<T> Result = new Result<T>();
        Result.setStatus(true);
        Result.setCode(200);
        Result.setMsg("success");
        return Result;
    }


    public static <T> Result<T> error() {
        Result<T> Result = new Result<T>();
        Result.setStatus(false);
        Result.setCode(500);
        Result.setMsg("error");
        return Result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> Result = new Result<T>();
        Result.setStatus(false);
        Result.setCode(500);
        Result.setMsg(message);
        return Result;
    }


}
