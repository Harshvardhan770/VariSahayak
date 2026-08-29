package com.varisahayak.data.repository;

import com.varisahayak.core.common.Clock;
import com.varisahayak.core.common.DispatcherProvider;
import com.varisahayak.data.local.VariSahayakDatabase;
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
public final class ProfileRepositoryImpl_Factory implements Factory<ProfileRepositoryImpl> {
  private final Provider<SupabaseClient> supabaseProvider;

  private final Provider<VariSahayakDatabase> databaseProvider;

  private final Provider<Clock> clockProvider;

  private final Provider<DispatcherProvider> dispatchersProvider;

  private ProfileRepositoryImpl_Factory(Provider<SupabaseClient> supabaseProvider,
      Provider<VariSahayakDatabase> databaseProvider, Provider<Clock> clockProvider,
      Provider<DispatcherProvider> dispatchersProvider) {
    this.supabaseProvider = supabaseProvider;
    this.databaseProvider = databaseProvider;
    this.clockProvider = clockProvider;
    this.dispatchersProvider = dispatchersProvider;
  }

  @Override
  public ProfileRepositoryImpl get() {
    return newInstance(supabaseProvider.get(), databaseProvider.get(), clockProvider.get(), dispatchersProvider.get());
  }

  public static ProfileRepositoryImpl_Factory create(Provider<SupabaseClient> supabaseProvider,
      Provider<VariSahayakDatabase> databaseProvider, Provider<Clock> clockProvider,
      Provider<DispatcherProvider> dispatchersProvider) {
    return new ProfileRepositoryImpl_Factory(supabaseProvider, databaseProvider, clockProvider, dispatchersProvider);
  }

  public static ProfileRepositoryImpl newInstance(SupabaseClient supabase,
      VariSahayakDatabase database, Clock clock, DispatcherProvider dispatchers) {
    return new ProfileRepositoryImpl(supabase, database, clock, dispatchers);
  }
}
