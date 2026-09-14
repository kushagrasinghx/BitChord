using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using Windows.Graphics;
using WinRT.Interop;

namespace BitChord.WinUI;

/// <summary>
/// Main BitChord surface.  The shell deliberately uses stable Grid, StackPanel,
/// ListView and Button primitives instead of NavigationView: the companion must
/// also start reliably on machines with older Windows App SDK resources.
/// </summary>
public sealed class MainWindow : Window
{
    private readonly Grid _root = new();
    private readonly Grid _contentHost = new();
    private readonly TextBlock _pageTitle = new();
    private readonly TextBlock _pageSubtitle = new();
    private readonly Button _globalSearch = new();
    private readonly Button _playButton;
    private readonly Slider _progressSlider;
    private readonly TextBlock _playerTitle = new();
    private readonly TextBlock _playerArtist = new();
    private readonly TextBlock _statusText = new();
    private readonly Dictionary<string, Button> _navigationButtons = new();
    private readonly Dictionary<string, object> _memorySettings = new();
    private readonly List<Track> _queue = new()
    {
        new("The Mother We Share", "CHVRCHES", "3:13"),
        new("Intro", "The xx", "2:07"),
        new("Nightcall", "Kavinsky", "4:18"),
        new("Wait", "M83", "3:58")
    };

    private bool _isPlaying = true;
    private int _currentQueueIndex;
    private string _currentPage = "home";

    private static readonly SolidColorBrush PageBrush = Brush(13, 15, 22);
    private static readonly SolidColorBrush SurfaceBrush = Brush(35, 38, 52, 235);
    private static readonly SolidColorBrush SurfaceStrongBrush = Brush(55, 51, 86, 245);
    private static readonly SolidColorBrush BorderBrush = Brush(80, 80, 110, 170);
    private static readonly SolidColorBrush PrimaryBrush = Brush(248, 248, 252);
    private static readonly SolidColorBrush SecondaryBrushValue = Brush(169, 170, 188);
    private static readonly SolidColorBrush AccentBrush = Brush(181, 156, 255);
    private static readonly SolidColorBrush AccentStrongBrush = Brush(141, 107, 255);

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hWnd);

    public MainWindow()
    {
        Title = "BitChord";
        _playButton = CreateButton("Ⅱ", "PlayerPlayButton", (_, _) => TogglePlayback(), accent: true);
        _progressSlider = new Slider
        {
            Minimum = 0,
            Maximum = 100,
            Value = 42,
            Width = 180,
            VerticalAlignment = VerticalAlignment.Center
        };
        SetAutomation(_progressSlider, "PlayerProgressSlider", "Playback position");

        BuildShell();
        ConfigureWindow();
        ShowPage("home");
    }

    private void BuildShell()
    {
        _root.Background = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0, 0),
            EndPoint = new Windows.Foundation.Point(1, 1),
            GradientStops =
            {
                new GradientStop { Color = ColorHelper.FromArgb(255, 24, 20, 45), Offset = 0 },
                new GradientStop { Color = ColorHelper.FromArgb(255, 13, 15, 22), Offset = .52 },
                new GradientStop { Color = ColorHelper.FromArgb(255, 16, 28, 43), Offset = 1 }
            }
        };
        _root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        _root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var body = new Grid { Margin = new Thickness(18, 12, 18, 0) };
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(238) });
        body.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        body.Children.Add(CreateSidebar());

        var main = new Grid { RowSpacing = 16 };
        main.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        main.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        main.Children.Add(CreateTopBar());
        Grid.SetRow(_contentHost, 1);
        main.Children.Add(_contentHost);
        Grid.SetColumn(main, 1);
        body.Children.Add(main);

        Grid.SetRow(body, 0);
        _root.Children.Add(body);
        var player = CreatePlayerBar();
        Grid.SetRow(player, 1);
        _root.Children.Add(player);
        Content = _root;
    }

    private FrameworkElement CreateSidebar()
    {
        var panel = new StackPanel { Spacing = 4, Margin = new Thickness(4, 8, 18, 18) };
        var brand = new Grid { Margin = new Thickness(8, 4, 8, 26) };
        brand.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        brand.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        brand.Children.Add(new Border
        {
            Width = 46,
            Height = 46,
            CornerRadius = new CornerRadius(14),
            Background = AccentStrongBrush,
            Child = new TextBlock
            {
                Text = "B",
                FontSize = 27,
                FontWeight = Microsoft.UI.Text.FontWeights.Bold,
                Foreground = PrimaryBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        });
        var brandText = new StackPanel { Margin = new Thickness(12, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
        brandText.Children.Add(new TextBlock { Text = "BitChord", FontSize = 20, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Foreground = PrimaryBrush });
        brandText.Children.Add(new TextBlock { Text = "Music, in full color", FontSize = 11, Foreground = SecondaryBrushValue });
        Grid.SetColumn(brandText, 1);
        brand.Children.Add(brandText);
        panel.Children.Add(brand);

        AddNavigationButton(panel, "⌂  Home", "home", "NavHomeButton");
        AddNavigationButton(panel, "✦  Explore", "explore", "NavExploreButton");
        AddNavigationButton(panel, "♫  Library", "library", "NavLibraryButton");
        AddNavigationButton(panel, "⇩  Downloads", "downloads", "NavDownloadsButton");
        AddNavigationButton(panel, "≡  Queue", "queue", "NavQueueButton");

        panel.Children.Add(new Border { Height = 1, Background = BorderBrush, Margin = new Thickness(14, 18, 14, 12) });
        AddNavigationButton(panel, "⚙  Settings", "settings", "NavSettingsButton");
        var help = CreateButton("ⓘ  About BitChord", "NavAboutButton", (_, _) => ShowAboutDialog(), accent: false);
        help.HorizontalContentAlignment = HorizontalAlignment.Left;
        help.Margin = new Thickness(4, 2, 4, 0);
        panel.Children.Add(help);
        return panel;
    }

    private void AddNavigationButton(Panel panel, string label, string tag, string automationId)
    {
        var button = CreateButton(label, automationId, (_, _) => ShowPage(tag));
        button.Tag = tag;
        button.HorizontalContentAlignment = HorizontalAlignment.Left;
        button.HorizontalAlignment = HorizontalAlignment.Stretch;
        button.Margin = new Thickness(4, 2, 4, 2);
        button.Padding = new Thickness(14, 11, 12, 11);
        _navigationButtons[tag] = button;
        panel.Children.Add(button);
    }

    private FrameworkElement CreateTopBar()
    {
        var bar = new Grid { ColumnSpacing = 14, Margin = new Thickness(22, 4, 22, 0) };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        _globalSearch.Content = "Search artists, albums, songs and playlists";
        _globalSearch.Width = 420;
        _globalSearch.HorizontalAlignment = HorizontalAlignment.Left;
        SetAutomation(_globalSearch, "GlobalSearchBox", "Search music");
        _globalSearch.Click += (_, _) => ShowPage("explore");
        bar.Children.Add(_globalSearch);

        var signedIn = CreateButton("Sign in", "TopSignInButton", (_, _) =>
        {
            ShowPage("settings");
            SetStatus("Account sign-in is ready to connect.");
        }, accent: true);
        Grid.SetColumn(signedIn, 1);
        bar.Children.Add(signedIn);

        var account = CreateButton("◉", "TopAccountButton", (_, _) => ShowPage("settings"));
        account.Width = 42;
        ToolTipService.SetToolTip(account, "Account and integrations");
        Grid.SetColumn(account, 2);
        bar.Children.Add(account);

        var status = new Border
        {
            Background = SurfaceBrush,
            CornerRadius = new CornerRadius(14),
            Padding = new Thickness(12, 8, 12, 8),
            Child = _statusText
        };
        _statusText.Text = "Ready to listen";
        _statusText.FontSize = 12;
        _statusText.Foreground = SecondaryBrushValue;
        Grid.SetColumn(status, 3);
        bar.Children.Add(status);
        return bar;
    }

    private FrameworkElement CreatePlayerBar()
    {
        var outer = new Border
        {
            Margin = new Thickness(26, 0, 26, 18),
            Padding = new Thickness(16, 12, 16, 12),
            Background = SurfaceBrush,
            BorderBrush = BorderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(20)
        };
        var grid = new Grid { ColumnSpacing = 14 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(280) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var track = new Grid { ColumnSpacing = 12 };
        track.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        track.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        track.Children.Add(new Border
        {
            Width = 52,
            Height = 52,
            CornerRadius = new CornerRadius(12),
            Background = Brush(64, 141, 107, 255),
            Child = new TextBlock { Text = "♫", FontSize = 28, Foreground = PrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        });
        var labels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        _playerTitle.Text = "Midnight City";
        _playerTitle.FontWeight = Microsoft.UI.Text.FontWeights.SemiBold;
        _playerArtist.Text = "M83  •  Hurry Up, We're Dreaming";
        _playerArtist.FontSize = 12;
        _playerArtist.Foreground = SecondaryBrushValue;
        labels.Children.Add(_playerTitle);
        labels.Children.Add(_playerArtist);
        Grid.SetColumn(labels, 1);
        track.Children.Add(labels);
        grid.Children.Add(track);

        var progress = new Grid { ColumnSpacing = 10, VerticalAlignment = VerticalAlignment.Center };
        progress.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        progress.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        progress.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        progress.Children.Add(new TextBlock { Text = "1:42", FontSize = 11, Foreground = SecondaryBrushValue, VerticalAlignment = VerticalAlignment.Center });
        Grid.SetColumn(_progressSlider, 1);
        progress.Children.Add(_progressSlider);
        var length = new TextBlock { Text = "4:03", FontSize = 11, Foreground = SecondaryBrushValue, VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(length, 2);
        progress.Children.Add(length);
        Grid.SetColumn(progress, 1);
        grid.Children.Add(progress);

        var controls = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        controls.Children.Add(CreateButton("‹", "PlayerPreviousButton", (_, _) => PreviousTrack()));
        controls.Children.Add(_playButton);
        controls.Children.Add(CreateButton("›", "PlayerNextButton", (_, _) => NextTrack()));
        Grid.SetColumn(controls, 2);
        grid.Children.Add(controls);

        var right = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        right.Children.Add(CreateButton("🔊", "PlayerVolumeButton", (_, _) => SetStatus("Volume is controlled by Windows.")));
        right.Children.Add(CreateButton("↗", "PlayerExpandButton", (_, _) => ShowNowPlaying()));
        Grid.SetColumn(right, 3);
        grid.Children.Add(right);
        outer.Child = grid;
        return outer;
    }

    private void ShowPage(string tag)
    {
        _currentPage = tag;
        foreach (var pair in _navigationButtons)
        {
            pair.Value.Background = pair.Key == tag ? Brush(70, 58, 120, 210) : new SolidColorBrush(Colors.Transparent);
            pair.Value.Foreground = pair.Key == tag ? PrimaryBrush : SecondaryBrushValue;
        }

        var content = tag switch
        {
            "explore" => CreateExplorePage(),
            "library" => CreateLibraryPage(),
            "downloads" => CreateDownloadsPage(),
            "queue" => CreateQueuePage(),
            "settings" => CreateSettingsPage(),
            _ => CreateHomePage()
        };
        _contentHost.Children.Clear();
        _contentHost.Children.Add(content);
        _pageTitle.Text = PageTitle(tag);
        _pageSubtitle.Text = PageSubtitle(tag);
    }

    private FrameworkElement CreatePage(string title, string subtitle)
    {
        var scroll = new ScrollViewer
        {
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            Padding = new Thickness(22, 2, 30, 32)
        };
        var stack = new StackPanel { Spacing = 18, MaxWidth = 1160 };
        stack.Children.Add(new TextBlock { Text = title, FontSize = 34, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Foreground = PrimaryBrush });
        stack.Children.Add(new TextBlock { Text = subtitle, FontSize = 16, Foreground = SecondaryBrushValue, Margin = new Thickness(0, -10, 0, 12) });
        scroll.Content = stack;
        return scroll;
    }

    private FrameworkElement CreateHomePage()
    {
        var scroll = (ScrollViewer)CreatePage("Good evening, Alex", "Find your next favorite sound.");
        var stack = (StackPanel)scroll.Content;
        stack.Children.Add(CreateSignInBanner());

        var heroGrid = new Grid { ColumnSpacing = 16 };
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(2, GridUnitType.Star) });
        heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var hero = CreateCard();
        hero.MinHeight = 230;
        var heroContent = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, Spacing = 8 };
        heroContent.Children.Add(Label("YOUR DAILY MIX", 12, AccentBrush));
        heroContent.Children.Add(Label("A little bit of everything", 27, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        heroContent.Children.Add(Label("M83, CHVRCHES, The xx and more", 14, SecondaryBrushValue));
        heroContent.Children.Add(CreateButton("Play mix", "HomePlayMixButton", (_, _) =>
        {
            _isPlaying = true;
            UpdatePlayButton();
            SetStatus("Daily Mix started");
        }, accent: true));
        hero.Child = heroContent;
        heroGrid.Children.Add(hero);

        var stats = CreateCard();
        Grid.SetColumn(stats, 1);
        var statsContent = new StackPanel { Spacing = 8 };
        statsContent.Children.Add(Label("LISTENING STATS", 12, AccentBrush));
        statsContent.Children.Add(Label("12h 42m", 32, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        statsContent.Children.Add(Label("this week", 14, SecondaryBrushValue));
        statsContent.Children.Add(new ProgressBar { Value = 68, Margin = new Thickness(0, 12, 0, 0) });
        statsContent.Children.Add(Label("You're on a 6 day streak", 13, SecondaryBrushValue));
        statsContent.Children.Add(CreateButton("View listening history", "HomeHistoryButton", (_, _) =>
        {
            ShowPage("library");
            SetStatus("Listening history is part of your library.");
        }));
        stats.Child = statsContent;
        heroGrid.Children.Add(stats);
        stack.Children.Add(heroGrid);

        stack.Children.Add(SectionHeading("Jump back in", "HomeRecentlyPlayedHeading"));
        var recentScroll = new ScrollViewer { HorizontalScrollBarVisibility = ScrollBarVisibility.Auto, VerticalScrollBarVisibility = ScrollBarVisibility.Disabled };
        var recent = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
        foreach (var track in new[]
        {
            new Track("Midnight City", "M83", "4:03"),
            new Track("Clearest Blue", "CHVRCHES", "3:53"),
            new Track("Intro", "The xx", "2:07"),
            new Track("Nightcall", "Kavinsky", "4:18")
        })
            recent.Children.Add(CreateTrackCard(track, "HomeTrack"));
        recentScroll.Content = recent;
        stack.Children.Add(recentScroll);
        stack.Children.Add(SectionHeading("Made for your mood", "HomeMoodHeading"));
        stack.Children.Add(CreateMoodRow());
        return scroll;
    }

    private FrameworkElement CreateExplorePage()
    {
        var scroll = (ScrollViewer)CreatePage("Explore", "Find a new favorite, browse moods, or search your library.");
        var stack = (StackPanel)scroll.Content;
        var search = new TextBox { PlaceholderText = "Search artists, albums, songs and playlists", Height = 48 };
        SetAutomation(search, "ExploreSearchBox", "Explore search");
        search.KeyDown += (_, args) =>
        {
            if (args.Key == Windows.System.VirtualKey.Enter &&
                !string.IsNullOrWhiteSpace(search.Text))
                SetStatus($"Searching for “{search.Text.Trim()}”");
        };
        stack.Children.Add(search);
        stack.Children.Add(SectionHeading("Made for you", "ExploreMadeForYouHeading"));
        var cards = new Grid { ColumnSpacing = 12 };
        for (var i = 0; i < 3; i++) cards.ColumnDefinitions.Add(new ColumnDefinition());
        AddExploreCard(cards, 0, "Focus Flow", "Ambient electronic for deep work", "ExploreFocusPlayButton");
        AddExploreCard(cards, 1, "Late Night", "Soft lights, slow rhythms", "ExploreLateNightPlayButton");
        AddExploreCard(cards, 2, "Discover Weekly", "30 tracks picked for you", "ExploreDiscoverPlayButton");
        stack.Children.Add(cards);
        stack.Children.Add(SectionHeading("Browse by mood & genre", "ExploreMoodsHeading"));
        stack.Children.Add(CreateMoodGrid());
        return scroll;
    }

    private FrameworkElement CreateLibraryPage()
    {
        var scroll = (ScrollViewer)CreatePage("Your library", "Everything you saved, ready offline.");
        var stack = (StackPanel)scroll.Content;
        var cards = new Grid { ColumnSpacing = 14 };
        for (var i = 0; i < 3; i++) cards.ColumnDefinitions.Add(new ColumnDefinition());
        AddLibrarySummary(cards, 0, "♫", "Liked songs", "248 songs", "LibraryLikedSongsButton");
        AddLibrarySummary(cards, 1, "◈", "Playlists", "12 playlists", "LibraryPlaylistsButton");
        AddLibrarySummary(cards, 2, "⇩", "Offline", "36 downloads", "LibraryOfflineButton");
        stack.Children.Add(cards);
        stack.Children.Add(SectionHeading("Your collections", "LibraryCollectionsHeading"));
        var collectionRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
        collectionRow.Children.Add(CreateCollectionCard("On this PC", "Local music", "LibraryLocalMusicButton"));
        collectionRow.Children.Add(CreateCollectionCard("Replay", "Your listening story", "LibraryReplayButton"));
        collectionRow.Children.Add(CreateCollectionCard("New playlist", "Create a collection", "LibraryNewPlaylistButton", true));
        stack.Children.Add(collectionRow);
        stack.Children.Add(SectionHeading("Recently added", "LibraryRecentlyAddedHeading"));
        var list = new StackPanel { Spacing = 4 };
        foreach (var track in new[]
        {
            new Track("Wait", "M83", "3:58"),
            new Track("The Mother We Share", "CHVRCHES", "3:13"),
            new Track("Intro", "The xx", "2:07")
        })
            list.Children.Add(CreateTrackRow(track, "LibraryTrack"));
        stack.Children.Add(list);
        return scroll;
    }

    private FrameworkElement CreateDownloadsPage()
    {
        var scroll = (ScrollViewer)CreatePage("Downloads", "Your music, even when the signal disappears.");
        var stack = (StackPanel)scroll.Content;
        var storage = CreateCard();
        var storageGrid = new Grid();
        storageGrid.ColumnDefinitions.Add(new ColumnDefinition());
        storageGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        storageGrid.Children.Add(Label("Storage", 18, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        var storageLabel = Label("2.4 GB of 32 GB", 13, SecondaryBrushValue);
        Grid.SetColumn(storageLabel, 1);
        storageGrid.Children.Add(storageLabel);
        var storagePanel = new StackPanel { Spacing = 12 };
        storagePanel.Children.Add(storageGrid);
        storagePanel.Children.Add(new ProgressBar { Value = 8 });
        storagePanel.Children.Add(Label("You have plenty of space for offline listening.", 13, SecondaryBrushValue));
        storage.Child = storagePanel;
        stack.Children.Add(storage);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        actions.Children.Add(CreateButton("Open downloads folder", "DownloadsOpenFolderButton", (_, _) => SetStatus("Downloads folder is ready to open.")));
        actions.Children.Add(CreateButton("Clear completed", "DownloadsClearButton", (_, _) => SetStatus("Completed downloads kept — choose individual tracks to remove.")));
        stack.Children.Add(actions);
        stack.Children.Add(SectionHeading("Downloaded", "DownloadsListHeading"));
        var list = new StackPanel { Spacing = 4 };
        foreach (var track in new[]
        {
            new Track("Midnight City", "FLAC • 42 MB", "✓"),
            new Track("Clearest Blue", "AAC • 8 MB", "✓"),
            new Track("Wait", "FLAC • 39 MB", "Downloading…")
        })
            list.Children.Add(CreateDownloadRow(track));
        stack.Children.Add(list);
        return scroll;
    }

    private FrameworkElement CreateQueuePage()
    {
        var scroll = (ScrollViewer)CreatePage("Up next", $"{_queue.Count} tracks in your queue.");
        var stack = (StackPanel)scroll.Content;
        var controls = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        var shuffle = CreateToggle("Shuffle queue", "QueueShuffleToggle", GetBool("shuffle", false));
        shuffle.Toggled += (_, _) =>
        {
            SetBool("shuffle", shuffle.IsOn);
            SetStatus(shuffle.IsOn ? "Queue shuffle enabled" : "Queue shuffle disabled");
        };
        controls.Children.Add(shuffle);
        controls.Children.Add(CreateButton("Clear queue", "QueueClearButton", (_, _) =>
        {
            _queue.Clear();
            SetStatus("Queue cleared");
            ShowPage("queue");
        }));
        stack.Children.Add(controls);

        var card = CreateCard();
        var content = new StackPanel { Spacing = 8 };
        content.Children.Add(Label("NOW PLAYING", 11, AccentBrush));
        content.Children.Add(CreateTrackRow(new Track("Midnight City", "M83", "3:03"), "QueueNowPlaying"));
        content.Children.Add(Label("NEXT", 11, AccentBrush, margin: new Thickness(8, 18, 0, 2)));
        for (var i = 0; i < _queue.Count; i++) content.Children.Add(CreateQueueRow(_queue[i], i));
        card.Child = content;
        stack.Children.Add(card);
        return scroll;
    }

    private FrameworkElement CreateSettingsPage()
    {
        var scroll = (ScrollViewer)CreatePage("Settings", "Tune BitChord to your setup.");
        var stack = (StackPanel)scroll.Content;

        stack.Children.Add(SectionHeading("Account & integrations", "SettingsAccountHeading"));
        var accountCard = CreateCard();
        var accountPanel = new StackPanel { Spacing = 10 };
        accountPanel.Children.Add(Label("Personalize your library and connect services.", 14, SecondaryBrushValue));
        accountPanel.Children.Add(CreateSettingRow("YouTube Music account", "Not signed in • tap to connect", CreateButton("Sign in", "SettingsYoutubeSignInButton", (_, _) => SetStatus("Sign-in flow is ready to connect."), true)));
        accountPanel.Children.Add(CreateSettingRow("Discord Rich Presence", "Share the song you are listening to", CreateToggle("Enabled", "SettingsDiscordToggle", GetBool("discord", false))));
        accountPanel.Children.Add(CreateSettingRow("ListenBrainz", "Scrobble listening history to the cloud", CreateToggle("Enabled", "SettingsListenBrainzToggle", GetBool("listenbrainz", false))));
        accountPanel.Children.Add(CreateSettingRow("Last.fm", "Scrobble tracks and now playing status", CreateToggle("Enabled", "SettingsLastFmToggle", GetBool("lastfm", false))));
        accountPanel.Children.Add(CreateButton("Manage account integrations", "SettingsIntegrationsButton", (_, _) => SetStatus("Account integration controls are shown above.")));
        accountCard.Child = accountPanel;
        stack.Children.Add(accountCard);

        stack.Children.Add(SectionHeading("Audio quality", "SettingsQualityHeading"));
        var qualityCard = CreateCard();
        var qualityPanel = new StackPanel { Spacing = 4 };
        qualityPanel.Children.Add(CreateSettingRow("Source", "YouTube Music plus configured module sources", CreateButton("Manage sources", "SettingsSourcesButton", (_, _) => SetStatus("Source manager is ready for module endpoints."))));
        qualityPanel.Children.Add(CreateSettingRow("On Wi-Fi", "Prefer the highest available quality", CreateComboBox("SettingsWifiQualityCombo", new[] { "Lossless", "High-res", "Balanced", "Data saver" }, GetString("wifiQuality", "High-res"))));
        qualityPanel.Children.Add(CreateSettingRow("On metered networks", "Limit data usage when a connection is metered", CreateComboBox("SettingsMobileQualityCombo", new[] { "High-res", "Balanced", "Data saver" }, GetString("mobileQuality", "Balanced"))));
        qualityPanel.Children.Add(CreateSettingRow("Dolby Atmos / spatial audio", "Use a compatible system output when available", CreateToggle("Enabled", "SettingsSpatialAudioToggle", GetBool("spatial", false))));
        qualityCard.Child = qualityPanel;
        stack.Children.Add(qualityCard);

        stack.Children.Add(SectionHeading("Downloads", "SettingsDownloadsHeading"));
        var downloadCard = CreateCard();
        var downloadPanel = new StackPanel { Spacing = 4 };
        downloadPanel.Children.Add(CreateSettingRow("Download quality", "Quality ceiling for offline tracks", CreateComboBox("SettingsDownloadQualityCombo", new[] { "FLAC / lossless", "AAC 256 kbps", "AAC 128 kbps" }, GetString("downloadQuality", "AAC 256 kbps"))));
        downloadPanel.Children.Add(CreateSettingRow("Wi-Fi only downloads", "Prevent downloads on metered connections", CreateToggle("On", "SettingsWifiOnlyToggle", GetBool("wifiOnly", true))));
        downloadPanel.Children.Add(CreateSettingRow("Export compatible downloads", "Keep files available to other desktop players", CreateToggle("Off", "SettingsExportDownloadsToggle", GetBool("exportDownloads", false))));
        downloadCard.Child = downloadPanel;
        stack.Children.Add(downloadCard);

        stack.Children.Add(SectionHeading("Playback", "SettingsPlaybackHeading"));
        var playbackCard = CreateCard();
        var playbackPanel = new StackPanel { Spacing = 4 };
        playbackPanel.Children.Add(CreateSettingRow("Crossfade", "Blend adjacent tracks for seamless playback", CreateSlider("SettingsCrossfadeSlider", 0, 12, GetDouble("crossfade", 4), value => SetStatus($"Crossfade: {value:0}s"))));
        playbackPanel.Children.Add(CreateSettingRow("Automix", "DJ-style transitions with beat matching", CreateToggle("Beta", "SettingsAutomixToggle", GetBool("automix", false))));
        playbackPanel.Children.Add(CreateSettingRow("Skip silence", "Skip quiet openings and endings", CreateToggle("Enabled", "SettingsSkipSilenceToggle", GetBool("skipSilence", false))));
        playbackPanel.Children.Add(CreateSettingRow("Playback speed", "Change speed without changing pitch", CreateComboBox("SettingsSpeedCombo", new[] { "0.5×", "0.75×", "1.0×", "1.25×", "1.5×", "2.0×" }, GetString("speed", "1.0×"))));
        playbackPanel.Children.Add(CreateSettingRow("Sleep timer", "Stop after this track or a fixed duration", CreateComboBox("SettingsSleepTimerCombo", new[] { "Off", "After this track", "15 minutes", "30 minutes", "60 minutes" }, GetString("sleepTimer", "Off"))));
        playbackPanel.Children.Add(CreateButton("Open system equalizer", "SettingsEqualizerButton", (_, _) => SetStatus("Windows sound settings can manage the system equalizer.")));
        playbackCard.Child = playbackPanel;
        stack.Children.Add(playbackCard);

        stack.Children.Add(SectionHeading("Appearance", "SettingsAppearanceHeading"));
        var appearanceCard = CreateCard();
        var appearancePanel = new StackPanel { Spacing = 4 };
        var themeCombo = CreateComboBox("SettingsThemeCombo", new[] { "System default", "Dark", "Light" }, GetString("theme", "Dark"));
        themeCombo.SelectionChanged += (_, _) =>
        {
            SetString("theme", themeCombo.SelectedItem?.ToString() ?? "Dark");
            ApplyTheme(themeCombo.SelectedItem?.ToString());
        };
        appearancePanel.Children.Add(CreateSettingRow("Theme", "Use the system theme or choose a fixed appearance", themeCombo));
        appearancePanel.Children.Add(CreateSettingRow("Liquid glass", "Translucent surfaces throughout the app", CreateToggle("On", "SettingsLiquidGlassToggle", GetBool("liquidGlass", true))));
        appearancePanel.Children.Add(CreateSettingRow("Dynamic artwork colors", "Tint the interface from the current album", CreateToggle("On", "SettingsArtworkColorsToggle", GetBool("artworkColors", true))));
        appearancePanel.Children.Add(CreateSettingRow("Reduce motion", "Use simpler animations and transitions", CreateToggle("Off", "SettingsReduceMotionToggle", GetBool("reduceMotion", false))));
        appearancePanel.Children.Add(CreateSettingRow("Animated cover art", "Show motion artwork where available", CreateToggle("On", "SettingsAnimatedArtworkToggle", GetBool("animatedArtwork", true))));
        appearanceCard.Child = appearancePanel;
        stack.Children.Add(appearanceCard);

        stack.Children.Add(SectionHeading("Lyrics & performance", "SettingsLyricsHeading"));
        var lyricsCard = CreateCard();
        var lyricsPanel = new StackPanel { Spacing = 4 };
        lyricsPanel.Children.Add(CreateSettingRow("Synced lyrics", "Show word-synced lyrics when a source provides them", CreateToggle("On", "SettingsSyncedLyricsToggle", GetBool("syncedLyrics", true))));
        lyricsPanel.Children.Add(CreateSettingRow("Blur unfocused lyrics", "Keep attention on the current line", CreateToggle("On", "SettingsLyricsBlurToggle", GetBool("lyricsBlur", true))));
        lyricsPanel.Children.Add(CreateSettingRow("High performance mode", "Prefer smooth animation and high refresh rates", CreateToggle("Beta", "SettingsHighPerformanceToggle", GetBool("highPerformance", false))));
        lyricsPanel.Children.Add(CreateSettingRow("Refresh rate", "Preferred display refresh rate for playback surfaces", CreateComboBox("SettingsRefreshRateCombo", new[] { "System default", "60 Hz", "120 Hz", "144 Hz" }, GetString("refreshRate", "System default"))));
        lyricsCard.Child = lyricsPanel;
        stack.Children.Add(lyricsCard);

        stack.Children.Add(SectionHeading("Local music & storage", "SettingsLocalMusicHeading"));
        var localCard = CreateCard();
        var localPanel = new StackPanel { Spacing = 4 };
        localPanel.Children.Add(CreateSettingRow("Local music folder", "All audio folders on this PC", CreateButton("Choose folder", "SettingsChooseFolderButton", (_, _) => SetStatus("Folder picker is ready to select a music folder."))));
        localPanel.Children.Add(CreateSettingRow("Filter non-music audio", "Hide recordings and unsupported files", CreateToggle("On", "SettingsFilterAudioToggle", GetBool("filterAudio", true))));
        localPanel.Children.Add(CreateSettingRow("Song cache limit", "Keep downloaded artwork and audio within this limit", CreateComboBox("SettingsCacheLimitCombo", new[] { "1 GB", "5 GB", "10 GB", "No limit" }, GetString("cacheLimit", "5 GB"))));
        localPanel.Children.Add(CreateButton("Clear song cache", "SettingsClearSongCacheButton", (_, _) => SetStatus("Song cache cleared.")));
        localPanel.Children.Add(CreateButton("Clear image cache", "SettingsClearImageCacheButton", (_, _) => SetStatus("Image cache cleared.")));
        localCard.Child = localPanel;
        stack.Children.Add(localCard);

        stack.Children.Add(SectionHeading("Your data & miscellaneous", "SettingsDataHeading"));
        var dataCard = CreateCard();
        var dataPanel = new StackPanel { Spacing = 4 };
        dataPanel.Children.Add(CreateSettingRow("Replay", "Review your listening story and monthly highlights", CreateButton("Open replay", "SettingsReplayButton", (_, _) => SetStatus("Replay is ready to open from Library."))));
        dataPanel.Children.Add(CreateSettingRow("Play next on swipe", "Swipe gestures add tracks to the queue or play them next", CreateToggle("Play next", "SettingsSwipeNextToggle", GetBool("swipeNext", true))));
        dataPanel.Children.Add(CreateSettingRow("Don't repeat songs", "Avoid repeating a track during radio playback", CreateToggle("On", "SettingsDontRepeatToggle", GetBool("dontRepeat", true))));
        dataPanel.Children.Add(CreateSettingRow("Stop music on close", "Stop playback when the window closes", CreateToggle("Off", "SettingsStopOnCloseToggle", GetBool("stopOnClose", false))));
        dataPanel.Children.Add(CreateSettingRow("App language", "System default", CreateComboBox("SettingsLanguageCombo", new[] { "System default", "English", "Spanish", "French", "German", "Hindi" }, GetString("language", "System default"))));
        dataPanel.Children.Add(CreateButton("Export data", "SettingsExportDataButton", (_, _) => SetStatus("Your BitChord data export is ready.")));
        dataPanel.Children.Add(CreateButton("Import data", "SettingsImportDataButton", (_, _) => SetStatus("Choose a BitChord backup to import.")));
        dataCard.Child = dataPanel;
        stack.Children.Add(dataCard);
        return scroll;
    }

    private Border CreateSignInBanner()
    {
        var panel = new StackPanel { Spacing = 8 };
        panel.Children.Add(Label("Make BitChord yours", 18, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        panel.Children.Add(Label("Sign in to sync your library, playlists, and listening history.", 14, SecondaryBrushValue));
        panel.Children.Add(CreateButton("Sign in to YouTube Music", "HomeSignInButton", (_, _) =>
        {
            ShowPage("settings");
            SetStatus("Account sign-in is ready to connect.");
        }, accent: true));
        var card = CreateCard(panel);
        card.Background = Brush(45, 48, 78, 210);
        return card;
    }

    private FrameworkElement CreateMoodRow()
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        foreach (var mood in new[] { ("Focus", 78, 91, 180), ("Energy", 210, 86, 130), ("Chill", 91, 106, 166), ("Night", 105, 75, 155) })
        {
            var button = CreateButton(mood.Item1, $"Mood{mood.Item1}Button", (_, _) =>
            {
                ShowPage("explore");
                SetStatus($"{mood.Item1} mood selected");
            });
            button.Width = 150;
            button.Height = 62;
            button.Background = Brush((byte)mood.Item2, (byte)mood.Item3, (byte)mood.Item4, 220);
            button.HorizontalContentAlignment = HorizontalAlignment.Left;
            row.Children.Add(button);
        }
        return row;
    }

    private FrameworkElement CreateMoodGrid()
    {
        var grid = new Grid { ColumnSpacing = 12, RowSpacing = 12 };
        for (var i = 0; i < 2; i++) grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(100) });
        for (var i = 0; i < 4; i++) grid.ColumnDefinitions.Add(new ColumnDefinition());
        var moods = new[] { "Focus", "Energy", "Chill", "Night", "Workout", "Acoustic", "Electronic", "New releases" };
        for (var i = 0; i < moods.Length; i++)
        {
            var moodTitle = moods[i];
            var button = CreateButton(moodTitle, $"ExploreMood{i}Button", (_, _) => SetStatus($"{moodTitle} category selected"));
            button.HorizontalContentAlignment = HorizontalAlignment.Left;
            button.VerticalContentAlignment = VerticalAlignment.Top;
            button.Padding = new Thickness(14);
            button.Background = Brush((byte)(95 + i * 12), (byte)(66 + i * 7), (byte)(145 + i * 5), 235);
            Grid.SetRow(button, i / 4);
            Grid.SetColumn(button, i % 4);
            grid.Children.Add(button);
        }
        return grid;
    }

    private void AddExploreCard(Grid grid, int column, string title, string description, string automationId)
    {
        var card = CreateCard();
        var panel = new StackPanel { Spacing = 10 };
        panel.Children.Add(Label(title, 20, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        panel.Children.Add(Label(description, 13, SecondaryBrushValue));
        panel.Children.Add(CreateButton("Play", automationId, (_, _) =>
        {
            _isPlaying = true;
            UpdatePlayButton();
            SetStatus($"{title} started");
        }, accent: true));
        card.Child = panel;
        Grid.SetColumn(card, column);
        grid.Children.Add(card);
    }

    private void AddLibrarySummary(Grid grid, int column, string icon, string title, string subtitle, string id)
    {
        var button = CreateButton($"{icon}  {title}\n{subtitle}", id, (_, _) => SetStatus($"{title} opened"));
        button.Height = 118;
        button.HorizontalContentAlignment = HorizontalAlignment.Left;
        button.VerticalContentAlignment = VerticalAlignment.Center;
        button.Padding = new Thickness(18);
        button.Background = SurfaceBrush;
        Grid.SetColumn(button, column);
        grid.Children.Add(button);
    }

    private FrameworkElement CreateCollectionCard(string title, string subtitle, string id, bool isNew = false)
    {
        var card = CreateCard();
        card.Width = 190;
        var panel = new StackPanel { Spacing = 7 };
        var art = new Border
        {
            Height = 130,
            Background = isNew ? Brush(54, 48, 86, 255) : Brush(65, 110, 125, 255),
            CornerRadius = new CornerRadius(14),
            Child = new TextBlock { Text = isNew ? "+" : "♫", FontSize = 42, Foreground = AccentBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        };
        panel.Children.Add(art);
        panel.Children.Add(Label(title, 15, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        panel.Children.Add(Label(subtitle, 12, SecondaryBrushValue));
        var button = CreateButton("Open", id, (_, _) => SetStatus($"{title} opened"));
        panel.Children.Add(button);
        card.Child = panel;
        return card;
    }

    private FrameworkElement CreateTrackCard(Track track, string prefix)
    {
        var card = CreateCard();
        card.Width = 190;
        card.Padding = new Thickness(12);
        var panel = new StackPanel { Spacing = 8 };
        panel.Children.Add(new Border
        {
            Height = 150,
            CornerRadius = new CornerRadius(14),
            Background = Brush((byte)(58 + track.Title.Length * 3 % 80), 92, 140, 255),
            Child = new TextBlock { Text = "♫", FontSize = 50, Foreground = PrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        });
        panel.Children.Add(Label(track.Title, 15, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        panel.Children.Add(Label(track.Artist, 12, SecondaryBrushValue));
        panel.Children.Add(CreateButton("Play", $"{prefix}{track.Title.Replace(" ", string.Empty)}PlayButton", (_, _) => PlayTrack(track)));
        card.Child = panel;
        return card;
    }

    private FrameworkElement CreateTrackRow(Track track, string prefix)
    {
        var row = new Grid { Margin = new Thickness(8, 6, 8, 6), ColumnSpacing = 12 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(new Border
        {
            Width = 48,
            Height = 48,
            CornerRadius = new CornerRadius(10),
            Background = Brush(64, 141, 107, 255),
            Child = new TextBlock { Text = "♫", FontSize = 25, Foreground = PrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        });
        var details = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        details.Children.Add(Label(track.Title, 14, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        details.Children.Add(Label(track.Artist, 12, SecondaryBrushValue));
        Grid.SetColumn(details, 1);
        row.Children.Add(details);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        actions.Children.Add(Label(track.Duration, 12, SecondaryBrushValue));
        actions.Children.Add(CreateButton("▶", $"{prefix}{track.Title.Replace(" ", string.Empty)}Button", (_, _) => PlayTrack(track)));
        Grid.SetColumn(actions, 2);
        row.Children.Add(actions);
        return row;
    }

    private FrameworkElement CreateDownloadRow(Track track)
    {
        var row = (Grid)CreateTrackRow(new Track(track.Title, track.Artist, track.Duration), "Download");
        return row;
    }

    private FrameworkElement CreateQueueRow(Track track, int index)
    {
        var row = new Grid { Margin = new Thickness(8, 4, 8, 4), ColumnSpacing = 12 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(Label($"{index + 1}", 13, SecondaryBrushValue));
        var details = new StackPanel();
        details.Children.Add(Label(track.Title, 14, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        details.Children.Add(Label(track.Artist, 12, SecondaryBrushValue));
        Grid.SetColumn(details, 1);
        row.Children.Add(details);
        var action = CreateButton("Remove", $"QueueRemove{index}Button", (_, _) =>
        {
            if (index < _queue.Count) _queue.RemoveAt(index);
            ShowPage("queue");
        });
        Grid.SetColumn(action, 2);
        row.Children.Add(action);
        return row;
    }

    private Border CreateSettingRow(string title, string subtitle, FrameworkElement trailing)
    {
        var row = new Grid { Margin = new Thickness(0, 10, 0, 10), ColumnSpacing = 18 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var labels = new StackPanel { Spacing = 3 };
        labels.Children.Add(Label(title, 15, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        labels.Children.Add(Label(subtitle, 12, SecondaryBrushValue));
        Grid.SetColumn(trailing, 1);
        trailing.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(labels);
        row.Children.Add(trailing);
        return new Border { BorderBrush = BorderBrush, BorderThickness = new Thickness(0, 0, 0, 1), Child = row };
    }

    private ToggleSwitch CreateToggle(string label, string id, bool isOn)
    {
        var toggle = new ToggleSwitch { Header = label, IsOn = isOn };
        SetAutomation(toggle, id, label);
        toggle.Toggled += (_, _) => SetBool(id, toggle.IsOn);
        return toggle;
    }

    private ComboBox CreateComboBox(string id, IEnumerable<string> values, string selected)
    {
        var combo = new ComboBox { Width = 170, ItemsSource = values.ToArray() };
        combo.SelectedItem = selected;
        SetAutomation(combo, id, "Selection");
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedItem is string value) SetString(id, value);
        };
        return combo;
    }

    private Slider CreateSlider(string id, double minimum, double maximum, double value, Action<double> onChanged)
    {
        var slider = new Slider { Width = 180, Minimum = minimum, Maximum = maximum, Value = value, StepFrequency = 1 };
        SetAutomation(slider, id, "Value");
        slider.ValueChanged += (_, args) =>
        {
            SetDouble(id, args.NewValue);
            onChanged(args.NewValue);
        };
        return slider;
    }

    private static Border CreateCard(FrameworkElement? child = null) => new()
    {
        Background = SurfaceBrush,
        BorderBrush = BorderBrush,
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(20),
        Padding = new Thickness(20),
        Child = child
    };

    private static TextBlock SectionHeading(string text, string automationId)
    {
        var heading = new TextBlock
        {
            Text = text,
            FontSize = 22,
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
            Foreground = PrimaryBrush,
            Margin = new Thickness(0, 16, 0, 0)
        };
        SetAutomation(heading, automationId, text);
        return heading;
    }

    private static TextBlock Label(string text, double size, Brush foreground, Windows.UI.Text.FontWeight? weight = null, Thickness? margin = null)
    {
        var label = new TextBlock { Text = text, FontSize = size, Foreground = foreground, TextWrapping = TextWrapping.Wrap };
        if (weight.HasValue) label.FontWeight = weight.Value;
        if (margin.HasValue) label.Margin = margin.Value;
        return label;
    }

    private static Button CreateButton(string label, string automationId, RoutedEventHandler handler, bool accent = false)
    {
        var button = new Button
        {
            Content = label,
            Padding = new Thickness(12, 8, 12, 8),
            Margin = new Thickness(0, 2, 0, 2),
            Background = accent ? AccentStrongBrush : new SolidColorBrush(Colors.Transparent),
            Foreground = PrimaryBrush,
            BorderBrush = accent ? AccentStrongBrush : BorderBrush,
            BorderThickness = accent ? new Thickness(0) : new Thickness(1),
            CornerRadius = new CornerRadius(12)
        };
        SetAutomation(button, automationId, label);
        button.Click += handler;
        return button;
    }

    private static void SetAutomation(DependencyObject control, string id, string name)
    {
        AutomationProperties.SetAutomationId(control, id);
        AutomationProperties.SetName(control, name);
    }

    private void ConfigureWindow()
    {
        var handle = WindowNative.GetWindowHandle(this);
        var id = Win32Interop.GetWindowIdFromWindow(handle);
        var appWindow = AppWindow.GetFromWindowId(id);
        appWindow.TitleBar.ExtendsContentIntoTitleBar = true;
        appWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        var scale = GetDpiForWindow(handle) / 96.0;
        appWindow.Resize(new SizeInt32((int)(1440 * scale), (int)(900 * scale)));
    }

    private void TogglePlayback()
    {
        _isPlaying = !_isPlaying;
        UpdatePlayButton();
        SetStatus(_isPlaying ? $"Playing {_playerTitle.Text}" : "Playback paused");
    }

    private void UpdatePlayButton() => _playButton.Content = _isPlaying ? "Ⅱ" : "▶";

    private void PlayTrack(Track track)
    {
        _playerTitle.Text = track.Title;
        _playerArtist.Text = track.Artist;
        _isPlaying = true;
        UpdatePlayButton();
        SetStatus($"Playing {track.Title}");
    }

    private void PreviousTrack()
    {
        _currentQueueIndex = Math.Max(0, _currentQueueIndex - 1);
        PlayTrack(_queue[_currentQueueIndex % Math.Max(1, _queue.Count)]);
    }

    private void NextTrack()
    {
        if (_queue.Count == 0)
        {
            SetStatus("Queue is empty");
            return;
        }
        _currentQueueIndex = (_currentQueueIndex + 1) % _queue.Count;
        PlayTrack(_queue[_currentQueueIndex]);
    }

    private void ShowNowPlaying()
    {
        var panel = new StackPanel { Spacing = 14, MaxWidth = 760 };
        panel.Children.Add(Label("NOW PLAYING", 12, AccentBrush));
        panel.Children.Add(Label(_playerTitle.Text, 38, PrimaryBrush, Microsoft.UI.Text.FontWeights.SemiBold));
        panel.Children.Add(Label(_playerArtist.Text, 16, SecondaryBrushValue));
        panel.Children.Add(new Border
        {
            Height = 260,
            Background = Brush(62, 78, 138, 255),
            CornerRadius = new CornerRadius(22),
            Child = new TextBlock { Text = "♫", FontSize = 100, Foreground = AccentBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        });
        panel.Children.Add(new Slider { Minimum = 0, Maximum = 100, Value = _progressSlider.Value });
        var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center, Spacing = 8 };
        actions.Children.Add(CreateButton("‹", "NowPlayingPreviousButton", (_, _) => PreviousTrack()));
        actions.Children.Add(CreateButton(_isPlaying ? "Pause" : "Play", "NowPlayingPlayButton", (_, _) => TogglePlayback(), true));
        actions.Children.Add(CreateButton("›", "NowPlayingNextButton", (_, _) => NextTrack()));
        panel.Children.Add(actions);
        _contentHost.Children.Clear();
        _contentHost.Children.Add(new ScrollViewer { Content = panel, Padding = new Thickness(70, 24, 70, 30) });
        _pageTitle.Text = "Now playing";
        _pageSubtitle.Text = "Full-screen cover art, lyrics, queue controls, and playback details.";
        SetStatus("Now playing");
    }

    private void ShowAboutDialog()
    {
        var dialog = new ContentDialog
        {
            Title = "BitChord",
            Content = "Aesthetic YouTube Music client with a native Windows companion. Search, browse, play, download, manage your library, and tune playback from one place.",
            PrimaryButtonText = "Open settings",
            CloseButtonText = "Close",
            XamlRoot = _root.XamlRoot
        };
        _ = dialog.ShowAsync();
    }

    private void ApplyTheme(string? theme)
    {
        _root.RequestedTheme = theme switch
        {
            "Light" => ElementTheme.Light,
            "Dark" => ElementTheme.Dark,
            _ => ElementTheme.Default
        };
    }

    private void SetStatus(string message) => _statusText.Text = message;

    private string PageTitle(string tag) => tag switch
    {
        "explore" => "Explore",
        "library" => "Your library",
        "downloads" => "Downloads",
        "queue" => "Up next",
        "settings" => "Settings",
        _ => "Home"
    };

    private string PageSubtitle(string tag) => tag switch
    {
        "explore" => "Find your next favorite sound.",
        "library" => "Saved music, playlists, and listening history.",
        "downloads" => "Your music, even when the signal disappears.",
        "queue" => "Control what plays next.",
        "settings" => "Tune BitChord to your setup.",
        _ => "Music, in full color."
    };

    private bool GetBool(string key, bool fallback)
    {
        if (TryGetSetting(key, out var value) && value is bool boolean)
            return boolean;
        var alias = key switch
        {
            "shuffle" => "QueueShuffleToggle",
            "discord" => "SettingsDiscordToggle",
            "listenbrainz" => "SettingsListenBrainzToggle",
            "lastfm" => "SettingsLastFmToggle",
            "spatial" => "SettingsSpatialAudioToggle",
            "wifiOnly" => "SettingsWifiOnlyToggle",
            "exportDownloads" => "SettingsExportDownloadsToggle",
            "automix" => "SettingsAutomixToggle",
            "skipSilence" => "SettingsSkipSilenceToggle",
            "liquidGlass" => "SettingsLiquidGlassToggle",
            "artworkColors" => "SettingsArtworkColorsToggle",
            "reduceMotion" => "SettingsReduceMotionToggle",
            "animatedArtwork" => "SettingsAnimatedArtworkToggle",
            "syncedLyrics" => "SettingsSyncedLyricsToggle",
            "lyricsBlur" => "SettingsLyricsBlurToggle",
            "highPerformance" => "SettingsHighPerformanceToggle",
            "filterAudio" => "SettingsFilterAudioToggle",
            "swipeNext" => "SettingsSwipeNextToggle",
            "dontRepeat" => "SettingsDontRepeatToggle",
            "stopOnClose" => "SettingsStopOnCloseToggle",
            _ => key
        };
        return TryGetSetting(alias, out value) && value is bool aliasedBoolean
            ? aliasedBoolean
            : fallback;
    }

    private double GetDouble(string key, double fallback)
    {
        if (TryGetSetting(key, out var value) && value is double number)
            return number;
        var alias = key == "crossfade" ? "SettingsCrossfadeSlider" : key;
        return TryGetSetting(alias, out value) && value is double aliasedNumber
            ? aliasedNumber
            : fallback;
    }

    private string GetString(string key, string fallback)
    {
        if (TryGetSetting(key, out var value) && value is string text)
            return text;
        var alias = key switch
        {
            "wifiQuality" => "SettingsWifiQualityCombo",
            "mobileQuality" => "SettingsMobileQualityCombo",
            "downloadQuality" => "SettingsDownloadQualityCombo",
            "speed" => "SettingsSpeedCombo",
            "sleepTimer" => "SettingsSleepTimerCombo",
            "refreshRate" => "SettingsRefreshRateCombo",
            "cacheLimit" => "SettingsCacheLimitCombo",
            "language" => "SettingsLanguageCombo",
            _ => key
        };
        return TryGetSetting(alias, out value) && value is string aliasedText
            ? aliasedText
            : fallback;
    }

    private bool TryGetSetting(string key, out object? value)
    {
        return _memorySettings.TryGetValue(key, out value);
    }

    private void SetBool(string key, bool value) => SetSetting(key, value);
    private void SetDouble(string key, double value) => SetSetting(key, value);
    private void SetString(string key, string value) => SetSetting(key, value);

    private void SetSetting(string key, object value)
    {
        _memorySettings[key] = value;
    }

    private static SolidColorBrush Brush(byte r, byte g, byte b, byte a = 255) =>
        new(ColorHelper.FromArgb(a, r, g, b));

    private sealed record Track(string Title, string Artist, string Duration);
}
