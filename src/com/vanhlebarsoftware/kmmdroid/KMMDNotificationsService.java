package com.vanhlebarsoftware.kmmdroid;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.util.Log;

public class KMMDNotificationsService extends Service
{
	private static final String TAG = KMMDNotificationsService.class.getSimpleName();
	private boolean runFlag = false;
	private KMMDNotificationScheduleUpdater kmmdNotificationScheduleUpdater;
	private NotificationManager kmmdNotifcationMgr;
	private Notification kmmdNotification;
	private KMMDroidApp kmmdApp;
	
	@Override
	public void onCreate() 
	{
		super.onCreate();
		this.kmmdApp = (KMMDroidApp) getApplication();
		this.kmmdNotificationScheduleUpdater = new KMMDNotificationScheduleUpdater();
	}

	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		this.runFlag = false;
		this.kmmdNotificationScheduleUpdater.interrupt();
		this.kmmdNotificationScheduleUpdater = null;
		this.kmmdApp.setServiceRunning(false);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		super.onStartCommand(intent, flags, startId);
		
		this.runFlag = true;
		this.kmmdApp.setServiceRunning(true);
		this.kmmdNotificationScheduleUpdater.start();
		
		return START_NOT_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) 
	{
		return null;
	}
	
	/****************************************************************************************************************
	 * Helper Functions
	 ***************************************************************************************************************/
	private void checkSchedules()
	{
		Cursor c = null;
		
		// Get our active schedules from the database.
		c = getContentResolver().query(KMMDProvider.CONTENT_SCHEDULE_URI, null, null, null, null);

		GregorianCalendar calToday = new GregorianCalendar();
		GregorianCalendar calYesterday = new GregorianCalendar();
		calYesterday = (GregorianCalendar) calToday.clone();
		calYesterday.add(Calendar.DAY_OF_MONTH, -1);
		String strToday = String.valueOf(calToday.get(Calendar.YEAR)) + "-" + String.valueOf(calToday.get(Calendar.MONTH)+ 1) + "-"
				+ String.valueOf(calToday.get(Calendar.DAY_OF_MONTH));
		String strYesterday = String.valueOf(calYesterday.get(Calendar.YEAR)) + "-" + String.valueOf(calYesterday.get(Calendar.MONTH)+ 1) + "-"
				+ String.valueOf(calYesterday.get(Calendar.DAY_OF_MONTH));
		
		// We have our open schedules from the database, now create the user defined period of cash flow.
		ArrayList<Schedule> Schedules = new ArrayList<Schedule>();
		
		Schedules = Schedule.BuildCashRequired(c, Schedule.padFormattedDate(strYesterday), Schedule.padFormattedDate(strToday), Transaction.convertToPennies("0.00"));

		// See if we even have any schedules either past due or due today.
		if(Schedules.size() > 0)
		{
			// See how many pastDue schedules we have
			int pastDue = 0;
			int dueToday = 0;
			for(int i=0; i < Schedules.size(); i++)
			{
				if( Schedules.get(i).isPastDue() )
					pastDue++;
				else if( Schedules.get(i).isDueToday())
					dueToday++;
			}
			
			this.kmmdNotifcationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			this.kmmdNotification = new Notification(R.id.kmmd_icon, "", 0);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, -1, new Intent(this, SchedulesActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
			this.kmmdNotification.when = System.currentTimeMillis();
			this.kmmdNotification.flags |= Notification.FLAG_AUTO_CANCEL;
			CharSequence notificationTitle = "Schedules due";
			String notificationSummary = "PastDue: " + String.valueOf(pastDue) + "\n" + "Due today: " + String.valueOf(dueToday);
			this.kmmdNotification.setLatestEventInfo(this, notificationTitle, notificationSummary, pendingIntent);
			this.kmmdNotifcationMgr.notify(0, this.kmmdNotification);
		
			Log.d(TAG, "Schedules past due: " + String.valueOf(pastDue));
			Log.d(TAG, "Schedules due today: " + String.valueOf(dueToday));
		}
		
		// close our cursor as we no longer need it.
		c.close();	
	}
	
	/**************************************************************************************************************
	 * Thread that will perform the actual updating of the notifications for schedules
	 *************************************************************************************************************/
	private class KMMDNotificationScheduleUpdater extends Thread
	{

		public KMMDNotificationScheduleUpdater()
		{
			super("KMMDNotificationScheduleUpdater-Updater");
		}
		
		@Override
		public void run()
		{
			KMMDNotificationsService kmmdNotificationsService = KMMDNotificationsService.this;
			
			while(kmmdNotificationsService.runFlag)
			{
				checkSchedules();
				stopSelf();
			}
		}
	}
}
