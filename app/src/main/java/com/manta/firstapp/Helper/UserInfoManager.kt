package com.manta.firstapp.Helper

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.*
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.manta.firstapp.Default.UserInfo
import com.manta.firstapp.R
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File

/**
 * by 변성욱
 * http 요청을 관리. 네트워킹의 결과(유저정보)를 가지고있음.
 */
class UserInfoManager {

    var mUserInfo: UserInfo? = null
        private set;


    companion object {

        private var INSTANCE: UserInfoManager? = null

        fun getInstance(): UserInfoManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE = UserInfoManager()
                return INSTANCE as UserInfoManager
            }
    }

    fun sendUserInfoToDB(context: Context, userInfo: UserInfo, url: String) {

        val req = object : StringRequest(
            Request.Method.POST, url,
            {
                Log.d("volley", it)
            },
            {
                Log.d("volleyError", it.message.toString())
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = mutableMapOf(Pair("email", userInfo.email))
                params["nickname"] = userInfo.nickname
                params["sex"] = userInfo.sex.toString()
                params["age"] = userInfo.age.toString()

                return params

            }
        }

        VolleyHelper.getInstance(context).addRequestQueue(req)

        //캐싱

        val fileContents = Gson().toJson(userInfo) //userInfo라는 객체를 json 형태로 바꾼다.
        val file = File(context.cacheDir, "account_" + userInfo.email) //휴대폰의 내부저장소 중 캐시저장소에 파일을 만든다.
        file.writeText(fileContents)  //파일에 준비한 json객체를 쓴다.

        mUserInfo = userInfo
    }

    fun checkBlacklisted(context: Context, email : String, onPass : ()->Unit, onRejected : ()->Unit )
    {
        val url = context.getString(R.string.urlToServer) + "checkBlacklisted/${email}"
        var request = JsonObjectRequest(Request.Method.GET, url, null,
            {
                it?.let {
                    val isBlacklisted = it.getInt("isBlacklisted")
                    if(isBlacklisted == 0)
                        onPass()
                    else
                        onRejected()
                }

            },
            {
                it?.let { Log.d("volley", it.message.toString()) }
            })

        context?.let { VolleyHelper.getInstance(it).addRequestQueue(request) }
    }

    /**
     * by 변성욱
     * email을 가진 유저에 대한 정보를 서버에 요청하고, mUserInfo에 셋팅한다.
     */
    fun requestUserInfo(context: Context, email: String, onResponse: (() -> Unit)? = null, onFailed: (() -> Unit)? = null) {

        //없으면 서버에 요청
        val url = context.getString(R.string.urlToServer) + "getUserInfo/"
        var request = JsonObjectRequest(
            Request.Method.POST, url,
            JSONObject(mapOf(Pair("email", email))),
            {

                if (it == null || it.length() <= 0) {
                    if (onFailed != null) onFailed()
                    return@JsonObjectRequest
                }

                it?.let { obj ->
                    val email = obj.getString("email")
                    val nickname = obj.getString("nickname")
                    val sex = obj.getInt("sex")
                    val age = obj.getInt("age")
                    val category = hashSetOf<Int>()

                    //카테고리를 내장메모리에서  얻는다.
                    val userInfoFromFile = getUserInfoFromFile(context, email)
                    var isShowSelfPost = true;
                    if (userInfoFromFile != null) {
                        mUserInfo?.categorys?.clear();
                        category.addAll(userInfoFromFile.categorys)
                        isShowSelfPost = userInfoFromFile.isShowSelfPost;
                    }
                    //만약 없으면 모든 카테고리를 추가한다.
                    else
                        GlobalHelper.getInstance(context).mCategory.forEachIndexed { index, s -> category.add(index) }


                    mUserInfo = UserInfo(email, nickname, sex, age, category, isShowSelfPost)

                    onResponse?.let { it() }
                }

            },
            {
                it.message?.let { it1 -> Log.d("volley", it1) }
                if (onFailed != null) onFailed()
            })

        VolleyHelper.getInstance(context).addRequestQueue(request)


    }

    /**
     * by 변성욱
     * 기기내부저장소에 저장해둔 설정파일로부터 유저정보를 불러온다.
     */
    private fun getUserInfoFromFile(context: Context, email: String): UserInfo? {
        val file = File(context.cacheDir, "account_" + email)
        if (file.exists())
            return Gson().fromJson(file.readText(), UserInfo::class.java)

        return null

    }



}