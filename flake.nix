{
  description = "Arbor - Manual DRO rethought";
  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs";
  };
  outputs = { self, nixpkgs, flake-utils }:
    (flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "34" "35" "36" ];
          buildToolsVersions = [ "34.0.0" "35.0.0" ];
          includeEmulator = false;
        };
      in {
        devShells = {
          default = pkgs.mkShell {
            buildInputs = with pkgs; [
              clojure
              nodejs
              jdk21

              androidComposition.androidsdk
            ];
            ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
            JAVA_HOME = "${pkgs.jdk21}";
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidComposition.androidsdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";
          };
        };
    }));
}

