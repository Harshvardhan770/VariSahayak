package com.varisahayak.core.network;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AndroidConnectivityObserver_Factory implements Factory<AndroidConnectivityObserver> {
  private final Provider<Context> contextProvider;

  private AndroidConnectivityObserver_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidConnectivityObserver get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidConnectivityObserver_Factory create(Provider<Context> contextProvider) {
    return new AndroidConnectivityObserver_Factory(contextProvider);
  }

  public static AndroidConnectivityObserver newInstance(Context context) {
    return new AndroidConnectivityObserver(context);
  }
}
