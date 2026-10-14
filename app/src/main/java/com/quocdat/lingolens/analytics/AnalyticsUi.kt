package com.quocdat.lingolens.analytics

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.data.remote.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object AnalyticsConsentCopy {
    const val WORDING="Cho phép LingoLens thu thập các sự kiện mở app, bắt đầu/hoàn thành/đạt bài và hoàn thành phiên ôn để cải thiện lộ trình? Dữ liệu gắn với tài khoản, không gồm ảnh, đáp án bạn nhập, mật khẩu hoặc token. Sự kiện thô trên hệ thống đang chạy được giữ tối đa 90 ngày. Bạn vẫn dùng đầy đủ app nếu từ chối và có thể tắt trong Cài đặt; tắt sẽ bỏ sự kiện chưa gửi, các sự kiện đã gửi hết hạn theo chính sách hoặc bị xóa khi xóa tài khoản."
    val BUTTONS=listOf("Cho phép","Không, cảm ơn")
    const val PRIVACY="Sự kiện được gắn với mã tài khoản và chỉ thu thập sau khi máy chủ xác nhận đồng ý. Ảnh, đáp án, mật khẩu và token không có trong sự kiện. Bản sao lưu có thể có thời hạn riêng; thời hạn backup/log còn chờ xác nhận nhà cung cấp lưu trữ trước phát hành. Tắt analytics không xóa lịch sử học của bạn."
}
class AnalyticsPromptPreferences(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences("analytics_ui",Context.MODE_PRIVATE)
    fun seen(account:Long)=preferences.getBoolean("choice-$account",false)
    fun rememberChoice(account:Long){preferences.edit { putBoolean("choice-$account",true) }}
}

@Composable
fun AnalyticsConsentHost(account:Long) {
    val app=LocalContext.current.applicationContext as LingoLensApplication
    val repository=app.container.analyticsRepository
    val preferences=remember{AnalyticsPromptPreferences(app)}
    var show by remember(account){mutableStateOf(false)}
    LaunchedEffect(account){
        repository.bestEffort{repository.initialize(account)}
        val state=repository.currentState(account)
        show=!preferences.seen(account) && state?.enabled!=true && state?.consentVersion==null
    }
    if(show)AnalyticsConsentDialog(account,repository,preferences){show=false}
}

@Composable
fun AnalyticsSettingsCard(account:Long) {
    val app=LocalContext.current.applicationContext as LingoLensApplication
    val repository=app.container.analyticsRepository
    val state by repository.observe(account).collectAsState(initial=null)
    val preferences=remember{AnalyticsPromptPreferences(app)}
    var show by remember(account){mutableStateOf(false)}
    var error by remember(account){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Dữ liệu giúp cải thiện học tập",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(if(state?.pendingOptOut==true)"Đã tắt trên máy • chờ đồng bộ" else if(state?.enabled==true)"Đang cho phép analytics" else "Analytics đang tắt",Modifier.weight(1f))
            Switch(checked=state?.enabled==true && state?.pendingOptOut!=true,onCheckedChange={enabled->
                if(enabled)show=true else scope.launch{
                    try{repository.optOut(account);preferences.rememberChoice(account);error=null}
                    catch(cancel:CancellationException){throw cancel}catch(failure:Exception){error=failure.toUserMessage(Gson())}
                }
            })
        }
        Text("Tự nguyện, không ảnh hoặc đáp án. Tắt không ảnh hưởng việc học.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }}
    if(show)AnalyticsConsentDialog(account,repository,preferences){show=false}
}

@Composable
private fun AnalyticsConsentDialog(account:Long,repository:AnalyticsRepository,preferences:AnalyticsPromptPreferences,onClose:()->Unit) {
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var privacy by remember{mutableStateOf(false)}
    var enableJob by remember{mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    fun decline(){
        enableJob?.cancel()
        scope.launch{
            try{repository.optOut(account);preferences.rememberChoice(account);onClose()}
            catch(cancel:CancellationException){throw cancel}catch(failure:Exception){error=failure.toUserMessage(Gson());busy=false}
        }
    }
    Dialog(onDismissRequest={decline()}) {
        Card {Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Bạn quyết định dữ liệu chia sẻ",style=MaterialTheme.typography.titleLarge)
            Text(AnalyticsConsentCopy.WORDING)
            TextButton(onClick={privacy=!privacy}){Text("Dữ liệu và quyền riêng tư")}
            if(privacy)Text(AnalyticsConsentCopy.PRIVACY,style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(busy)Text("Đang chờ máy chủ xác nhận…")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(modifier=Modifier.weight(1f).heightIn(min=48.dp),enabled=!busy,onClick={
                    busy=true;error=null
                    enableJob=scope.launch{
                        try{if(repository.enable(account)){preferences.rememberChoice(account);onClose()}else error="Chưa bật analytics. Hãy thử lại khi đăng nhập và có mạng."}
                        catch(cancel:CancellationException){throw cancel}catch(failure:Exception){error=failure.toUserMessage(Gson())}
                        finally{busy=false}
                    }
                }){Text(AnalyticsConsentCopy.BUTTONS[0])}
                OutlinedButton(modifier=Modifier.weight(1f).heightIn(min=48.dp),onClick={decline()}){Text(AnalyticsConsentCopy.BUTTONS[1])}
            }
        }}
    }
}
