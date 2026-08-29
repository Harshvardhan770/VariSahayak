package com.varisahayak.data.repository;

import com.varisahayak.core.common.DispatcherProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.github.jan.supabase.SupabaseClient;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class AuthRepositoryImpl_Factory implements Factory<AuthRepositoryImpl> {
  private final Provider<SupabaseClient> supabaseProvider;

  private final Provider<DispatcherProvider> dispatchersProvider;

  private AuthRepositoryImpl_Factory(Provider<SupabaseClient> supabaseProvider,
      Provider<DispatcherProvider> dispatchersProvider) {
    this.supabaseProvider = supabaseProvider;
    this.dispatchersProvider = dispatchersProvider;
  }

  @Override
  public AuthRepositoryImpl get() {
    return newInstance(supabaseProvider.get(), dispatchersProvider.get());
  }

  public static AuthRepositoryImpl_Factory create(Provider<SupabaseClient> supabaseProvider,
      Provider<DispatcherProvider> dispatchersProvider) {
    return new AuthRepositoryImpl_Factory(supabaseProvider, dispatchersProvider);
  }

  public static AuthRepositoryImpl newInstance(SupabaseClient supabase,
      DispatcherProvider dispatchers) {
    return new AuthRepositoryImpl(supabase, dispatchers);
  }
}
