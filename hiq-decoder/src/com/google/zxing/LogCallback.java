/*
 Copyright (C) 2015 Solon Li 
 */
package com.google.zxing;

/**
 * Callback which is invoked when there is a msg to log from core
 */
public interface LogCallback{
  void LogMsg(String msg, boolean isShowTimeDiff, int level);
  void LogMsg(String msg, boolean isShowTimeDiff);
}