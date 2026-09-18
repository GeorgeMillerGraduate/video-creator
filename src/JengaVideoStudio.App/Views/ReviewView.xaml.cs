using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;
namespace JengaVideoStudio.App.Views;

// Playback belongs to the view. Research, editing, rendering and publication remain in the ViewModel.
public partial class ReviewView : UserControl
{
    StudioViewModel? vm;
    readonly DispatcherTimer timer=new(){Interval=TimeSpan.FromMilliseconds(250)};
    bool updating;
    double pendingPosition;
    double? stopAt;
    public ReviewView()
    {
        InitializeComponent();Loaded+=Attach;Unloaded+=Detach;
        timer.Tick+=(_,_)=>
        {
            if(!Player.NaturalDuration.HasTimeSpan)return;
            updating=true;
            if(!Seek.IsMouseCaptureWithin)Seek.Value=Player.Position.TotalSeconds;
            PlaybackTime.Text=PresentationFiles.Time(Player.Position.TotalSeconds)+" / "+PresentationFiles.Time(Player.NaturalDuration.TimeSpan.TotalSeconds);
            updating=false;
            if(stopAt is {} end&&Player.Position.TotalSeconds>=end)Player.Pause();
        };
    }
    void Attach(object sender,RoutedEventArgs e)
    {
        if(vm!=null)return;
        vm=DataContext as StudioViewModel;if(vm==null)return;
        vm.ReleasePreview+=Release;vm.PlaybackRequested+=Display;vm.PropertyChanged+=Changed;
        if(vm.Preview!=null)Display(vm.Preview,0,null);
        timer.Start();
    }
    void Detach(object sender,RoutedEventArgs e)
    {
        timer.Stop();Release();
        if(vm!=null){vm.ReleasePreview-=Release;vm.PlaybackRequested-=Display;vm.PropertyChanged-=Changed;vm=null;}
    }
    void Changed(object? sender,PropertyChangedEventArgs e)
    {if(e.PropertyName==nameof(StudioViewModel.Preview)&&vm?.Preview!=null)Display(vm.Preview,0,null);}
    void Release(){Player.Stop();Player.Close();Player.Source=null;Seek.Value=0;PlaybackTime.Text="0:00 / 0:00";}
    void Display(Uri source,double start,double? end)
    {
        Player.Stop();Player.Close();pendingPosition=start;stopAt=end;Player.Source=source;Player.Pause();
    }
    void MediaOpened(object sender,RoutedEventArgs e)
    {
        if(!Player.NaturalDuration.HasTimeSpan)return;
        updating=true;Seek.Maximum=Math.Max(1,Player.NaturalDuration.TimeSpan.TotalSeconds);Seek.Value=pendingPosition;updating=false;
        Player.Position=TimeSpan.FromSeconds(pendingPosition);
    }
    void Play(object sender,RoutedEventArgs e){if(Player.Source!=null)Player.Play();}
    void Pause(object sender,RoutedEventArgs e)=>Player.Pause();
    void Stop(object sender,RoutedEventArgs e){Player.Stop();Seek.Value=0;}
    void SeekChanged(object sender,RoutedPropertyChangedEventArgs<double> e)
    {if(!updating&&Player!=null&&Player.NaturalDuration.HasTimeSpan)Player.Position=TimeSpan.FromSeconds(e.NewValue);}
    void MediaEnded(object sender,RoutedEventArgs e)=>Player.Pause();
    void MediaFailed(object sender,ExceptionRoutedEventArgs e)
    {if(vm!=null)vm.Detail="Preview could not play this file. Open the MP4 from Export to use your external player. "+e.ErrorException.Message;}
}
