using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Windows.Forms;


namespace GameSocket
{
    /// <summary>
    /// 
    /// Socket Server by Chying
    /// 
    /// 1. Reveived Image from Android
    /// 2. Call Matlab to do number recognition
    /// 3. Send the result about number recognition to Android
    /// 
    /// </summary>

    public class SocketT2h
    {
        public Socket _Socket { get; set; }
        public string _Name { get; set; }
        public SocketT2h(Socket socket)
        {
            this._Socket = socket;
        }
    }

    public partial class frm_Server : Form
    {
        private byte[] _buffer = new byte[65536];
        byte[] imgBuffer;
        byte[] aImage = new byte[950000];
        Image receivedImag;
        bool firstReceive, lastReceive;
        int totalBytes, start;
        string answer, nameFormat;
        MLApp.MLApp matlab; // Create the MATLAB instance 

        public List<SocketT2h> __ClientSockets { get; set; }
        List<string> _names = new List<string>();

        // 建立 Socket 等待client請求 透過TCP發送data
        private Socket _serverSocket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);

        public frm_Server()
        {
            InitializeComponent();
            CheckForIllegalCrossThreadCalls = false;
            __ClientSockets = new List<SocketT2h>();
        }

        private void frm_Server_Load(object sender, EventArgs e)
        {
            SetupServer();
        }

        private void SetupServer()
        {
            /// <summary>
            /// 啟動非同步連線 listen
            /// .Bind(): socket接收IPAddress跟IPEndPoint
            /// 佔用特定的port進入無窮迴圈, 利用socket.Accept()接受客戶端連線, 產生新Socket
            /// 建立新的Thread處理此連線, 所以需要一個Listener去處理此Socket連線.
            /// 有幾個連線就建立幾個 Thread
            /// </summary>
            lb_stt.Text = "Setting up server . . .";
            _serverSocket.Bind(new IPEndPoint(IPAddress.Parse("134.208.3.118"), 100));
            _serverSocket.Listen(1);
            _serverSocket.BeginAccept(new AsyncCallback(AppceptCallback), null);
        }

        private void AppceptCallback(IAsyncResult ar) // 等待 client 請求
        {
            // 把連接進來的client都存放到Items中並計數
            Socket socket = _serverSocket.EndAccept(ar); // 接受新的socket連進來
            __ClientSockets.Add(new SocketT2h(socket));
            list_Client.Items.Add(socket.RemoteEndPoint.ToString());

            lb_soluong.Text = "Clients are connected: " + __ClientSockets.Count.ToString();
            lb_stt.Text = "Client connected. . .";
            firstReceive = true;
            socket.BeginReceive(_buffer, 0, _buffer.Length, SocketFlags.None, new AsyncCallback(ReceiveCallback), socket);
            _serverSocket.BeginAccept(new AsyncCallback(AppceptCallback), null);
        }


        private void ReceiveCallback(IAsyncResult ar)
        {
            answer = null;
            byte[] recBuffer;
            Socket socket = (Socket)ar.AsyncState;
            if (socket.Connected)
            {
                int received;
                byte[] sz = new byte[4]; // 備妥準備接收client最初送過來的4個bytes的buffer

                try
                {
                    received = socket.EndReceive(ar); // 接收4個bytes // 得到新的socket來client端讀取data
                }
                catch (Exception)
                {
                    // client 關閉連線
                    for (int i = 0; i < __ClientSockets.Count; i++)
                    {
                        if (__ClientSockets[i]._Socket.RemoteEndPoint.ToString().Equals(socket.RemoteEndPoint.ToString()))
                        {
                            __ClientSockets.RemoveAt(i); // 把此client從list刪除
                            lb_soluong.Text = "Clients are connected: " + __ClientSockets.Count.ToString();
                        }
                    } 
                    return;
                }
                if (received != 0) // 若有東西傳過來
                {
                    // 接收第一批的資料 
                    // 1. 圖片大小（4個bytes）
                    byte[] buffer = new byte[4];
                    Array.Copy(_buffer, buffer, 4); // 複製4個bytes到buffer中
                    int size = 0;
                    size = (int)(buffer[0] + buffer[1] * Math.Pow(256, 1) + buffer[2] * Math.Pow(256, 2) + buffer[3] * Math.Pow(256, 3));
                    // 2. 宿舍名稱（12個bytes，ex:行雲一莊）
                    byte[] buffer2 = new byte[12];
                    Array.Copy(_buffer, 4, buffer2, 0, 12);
                    string MyDrom = Encoding.UTF8.GetString(buffer2);
                    // 3. 房號（3個bytes，ex:111）
                    byte[] buffer3 = new byte[3];
                    Array.Copy(_buffer, 16, buffer3, 0, 3);
                    string MyRoomNumber = Encoding.UTF8.GetString(buffer3);

                    // 接收第二批的資料，即整張影像
                    // 開始進入迴圈接收影像資料，因為socket可能無法一次完成接收
                    recBuffer = new byte[size]; // 配置buffer準備接收image
                    int total = 0, recv; // total為已接收資料量
                    while (total != size) // 迴圈持續，直到total == size，表示以全數接收完畢
                    {
                        recv = socket.Receive(recBuffer, total, size - total, SocketFlags.None); // 反覆接收未完成接收的影像資料
                        total += recv; // 累加新收資料量到total
                    }

                    // 另存圖片
                    MemoryStream ms = new MemoryStream(recBuffer);
                    Image receivedImag = Image.FromStream(ms);
                    nameFormat = SaveNameFormat(MyDrom, MyRoomNumber);
                    string name = "d:\\Matlab_ImageProcessModule\\DvmPhoto\\" + nameFormat + ".jpg";
                    receivedImag.Save(name);

                    // 把圖片名稱傳到matlab
                    string reponse = string.Empty;
                    reponse = CallMatlab(nameFormat);

                    // 顯示回傳的字串(rich_Text)及圖片(pictureBox1)
                    rich_Text.AppendText("\nServer: " + reponse); 
                    Bitmap showPIC = new Bitmap(name);
                    pictureBox1.SizeMode = PictureBoxSizeMode.Zoom; // 自動調整符合大小
                    pictureBox1.Image = showPIC;

                    // 回傳結果
                    Sendata(socket, reponse);

                }
                else // 收完了
                {
                    for (int i = 0; i < __ClientSockets.Count; i++)
                    {
                        if (__ClientSockets[i]._Socket.RemoteEndPoint.ToString().Equals(socket.RemoteEndPoint.ToString()))
                        {
                            __ClientSockets.RemoveAt(i);
                            lb_soluong.Text = "Total Client Number: " + __ClientSockets.Count.ToString();
                        }
                    }
                }
            }
        }

        void Sendata(Socket socket, string noidung)
        {   // 告訴android 我收到了
            byte[] data = Encoding.ASCII.GetBytes(noidung);
            socket.BeginSend(data, 0, data.Length, SocketFlags.None, new AsyncCallback(SendCallback), socket);
            _serverSocket.BeginAccept(new AsyncCallback(AppceptCallback), null);
        }
        private void SendCallback(IAsyncResult AR)
        {
            Socket socket = (Socket)AR.AsyncState; // 得到socket的狀態
            socket.EndSend(AR); // 完成送資料到android端
        }

        
        private void btnSend_Click(object sender, EventArgs e)
        {   // 送字串到android的部分 // 可刪除
            for (int i = 0; i < list_Client.SelectedItems.Count; i++)
            {
                string t = list_Client.SelectedItems[i].ToString();
                for (int j = 0; j < __ClientSockets.Count; j++)
                {
                    {
                        Sendata(__ClientSockets[j]._Socket, txt_Text.Text);
                    }
                }
            }
            rich_Text.AppendText("\nServer: " + txt_Text.Text);
        }
        
        
      
        private void pictureBox1_Click(object sender, EventArgs e)
        {
            Image pic = receivedImag; // 顯示收到的圖片
        }
        
        public string SaveNameFormat(string MyDorm, string MyRoomNumber)
        {   // 記錄當時時間，地點及房號待補
            string Drom = MyDorm;
            string RoomNumber = MyRoomNumber;
            // string time = DateTime.Now.ToString("yyyy-MM-dd_HH-mm-ss");
            string time = DateTime.Now.ToString("HH點mm分ss秒");
            string FileName = Drom + "_" + RoomNumber + "房_" + time;
            return FileName;
        }

        public string CallMatlab(string nameFormat)
        {
            
            matlab = new MLApp.MLApp(); // Create the MATLAB instance 
            matlab.Execute(@"cd d:\Matlab_ImageProcessModule"); // 執行 matlab
            object result = null; // Matlab output

            // Call the MATLAB function A ("A", return數, result, 參數a, b, c, ...);
            matlab.Feval("RunMatlab", 1, out result, nameFormat); 

            // 回傳 result
            object[] res = result as object[];
            answer = null;
            answer = res[0].ToString();

            return answer;
        }
    }
}
