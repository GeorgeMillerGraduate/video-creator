using System.Windows.Input;
namespace JengaVideoStudio.App;
public sealed class Command(Action<object?> execute,Func<bool>? allowed=null) : ICommand
{
    public bool CanExecute(object? p)=>allowed?.Invoke()??true;
    public void Execute(object? p)=>execute(p);
    public event EventHandler? CanExecuteChanged { add=>CommandManager.RequerySuggested+=value; remove=>CommandManager.RequerySuggested-=value; }
}
