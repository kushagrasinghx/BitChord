using Microsoft.UI.Xaml;
using System;
using System.IO;
using System.Threading.Tasks;

namespace BitChord.WinUI;

public sealed partial class App : Application
{
    private static readonly object LogLock = new();
    public static Window? MainWindow { get; private set; }

    public App()
    {
        AppDomain.CurrentDomain.UnhandledException += (_, eventArgs) =>
            WriteStartupDiagnostic(
                "AppDomain.CurrentDomain.UnhandledException",
                eventArgs.ExceptionObject);
        TaskScheduler.UnobservedTaskException += (_, eventArgs) =>
        {
            WriteStartupDiagnostic(
                "TaskScheduler.UnobservedTaskException",
                eventArgs.Exception);
            eventArgs.SetObserved();
        };

        try
        {
            InitializeComponent();
        }
        catch (Exception exception)
        {
            WriteStartupDiagnostic("App.InitializeComponent", exception);
            throw;
        }

        UnhandledException += OnUnhandledException;
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            MainWindow = new MainWindow();
            MainWindow.Activate();
        }
        catch (Exception exception)
        {
            WriteStartupDiagnostic("App.OnLaunched", exception);
            throw;
        }
    }

    private static void OnUnhandledException(
        object sender,
        Microsoft.UI.Xaml.UnhandledExceptionEventArgs args)
    {
        WriteStartupDiagnostic(
            "Microsoft.UI.Xaml.Application.UnhandledException",
            args.Exception);
    }

    private static void WriteStartupDiagnostic(string source, object exception)
    {
        try
        {
            var desktop = Environment.GetFolderPath(
                Environment.SpecialFolder.DesktopDirectory);
            var directory = string.IsNullOrWhiteSpace(desktop)
                ? Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData)
                : desktop;
            var logPath = Path.Combine(directory, "BitChord-startup.log");

            lock (LogLock)
            {
                File.AppendAllText(
                    logPath,
                    $"{DateTimeOffset.Now:O} [{source}]{Environment.NewLine}" +
                    $"{exception}{Environment.NewLine}{Environment.NewLine}");
            }
        }
        catch
        {
            // Diagnostics must never become another startup failure.
        }
    }
}
