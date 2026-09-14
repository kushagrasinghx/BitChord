using Microsoft.UI.Xaml.Controls;
using System.Collections.Generic;

namespace BitChord.WinUI.Pages;

public sealed partial class HomePage : Page
{
    public HomePage()
    {
        InitializeComponent();
        RecentTracks.ItemsSource = new[]
        {
            new Track("Midnight City", "M83"),
            new Track("Clearest Blue", "CHVRCHES"),
            new Track("Intro", "The xx"),
            new Track("Nightcall", "Kavinsky")
        };
    }

    private record Track(string Title, string Artist);
}
