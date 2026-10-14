package com.quocdat.lingolens.deletion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.data.remote.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun DeletionOutcome.message()=when(this){
    DeletionOutcome.DELETED->"Tài khoản và dữ liệu trên thiết bị này đã được xóa."
    DeletionOutcome.RETRY_REQUIRED->"Chưa có xác nhận xóa. Vào Cài đặt → Tài khoản và nhập lại mật khẩu để thử cùng yêu cầu."
    DeletionOutcome.UNCONFIRMED->"Không xác nhận được kết quả. Dữ liệu trên thiết bị vẫn được giữ; đăng nhập lại tài khoản liên quan để thử tiếp."
}

@Composable
fun AccountDeletionCard(account:Long){
    val repository=(LocalContext.current.applicationContext as LingoLensApplication).container.deletionRepository
    var show by remember(account){mutableStateOf(false)}
    var password by remember(account){mutableStateOf("")}
    var busy by remember(account){mutableStateOf(false)}
    var message by remember(account){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Tài khoản",style=MaterialTheme.typography.titleMedium)
        Text("Xóa lịch sử học, từ đã lưu và dữ liệu tài khoản trên máy chủ. Nội dung học chung được giữ.")
        OutlinedButton(onClick={password="";message=null;show=true}){Text("Xóa tài khoản")}
    }}
    if(show)AlertDialog(onDismissRequest={if(!busy){show=false;password=""}},
        title={Text("Xác nhận xóa tài khoản")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Hành động này không thể hoàn tác. Cần kết nối máy chủ. Chỉ dọn dữ liệu trên thiết bị sau khi có xác nhận xóa.")
            OutlinedTextField(value=password,onValueChange={password=it},enabled=!busy,singleLine=true,
                visualTransformation=PasswordVisualTransformation(),label={Text("Nhập lại mật khẩu")})
            message?.let{Text(it)}
            if(busy)Text("Đang xác nhận…")
        }},
        confirmButton={TextButton(enabled=!busy && password.isNotEmpty(),onClick={
            busy=true
            scope.launch{
                try{val result=repository.start(account,password);message=result.message();if(result==DeletionOutcome.DELETED)show=false}
                catch(cancel:CancellationException){throw cancel}catch(error:Exception){message=error.toUserMessage(Gson())}
                finally{password="";busy=false}
            }
        }){Text("Xác nhận xóa")}},
        dismissButton={TextButton(enabled=!busy,onClick={show=false;password=""}){Text("Hủy")}})
}

@Composable
fun DeletionRecoveryHost(){
    val repository=(LocalContext.current.applicationContext as LingoLensApplication).container.deletionRepository
    val pending by remember{repository.proofs.observe()}.collectAsState(initial=emptyList())
    var messages by remember{mutableStateOf<Map<Long,String>>(emptyMap())}
    var show by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    suspend fun check(){
        val result=mutableMapOf<Long,String>()
        for(proof in pending){
            try{val outcome=repository.resume(proof);if(outcome!=DeletionOutcome.DELETED)result[proof.accountId]=outcome.message()}
            catch(cancel:CancellationException){throw cancel}catch(_:Exception){result[proof.accountId]="Chưa kết nối được để xác nhận. Dữ liệu và mã yêu cầu vẫn được giữ."}
        }
        messages=result;show=result.isNotEmpty()
    }
    LaunchedEffect(pending.map{it.code}){if(pending.isNotEmpty())check()else{messages=emptyMap();show=false}}
    if(show)AlertDialog(onDismissRequest={show=false},title={Text("Yêu cầu xóa đang chờ xác nhận")},
        text={Column{messages.forEach{(account,message)->Text("Tài khoản #$account: $message")}}},
        confirmButton={TextButton(onClick={scope.launch{check()}}){Text("Kiểm tra lại")}},
        dismissButton={TextButton(onClick={show=false}){Text("Đóng")}})
}
