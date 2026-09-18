using System.Windows;
namespace JengaVideoStudio.App;
public partial class MainWindow : Window
{
    readonly StudioViewModel vm;
    bool approvedClose;
    public MainWindow()
    {
        InitializeComponent(); vm=new(); DataContext=vm;
        Width=Math.Max(MinWidth,Math.Min(1440,SystemParameters.WorkArea.Width-32));
        Height=Math.Max(MinHeight,Math.Min(920,SystemParameters.WorkArea.Height-32));
        Closing+=async (_,e)=>
        {
            if(approvedClose)return;
            e.Cancel=true;
            if(vm.Busy){MessageBox.Show("Cancel the current task before closing, so its project can be saved.");return;}
            if(await vm.ConfirmProjectChange()){approvedClose=true;Dispatcher.BeginInvoke(new Action(Close));}
        };
        Closed+=(_,_)=>vm.Dispose();
    }
}
